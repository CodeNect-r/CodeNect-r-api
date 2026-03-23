package com.lovable.ai_service.regeneration;

import com.lovable.ai_service.dto.AiProgressEvent;
import com.lovable.ai_service.dto.GeneratedFile;
import com.lovable.ai_service.dto.GenerationMode;
import com.lovable.ai_service.producer.AiProgressProducer;
import com.lovable.ai_service.service.AiClientService;
import com.lovable.ai_service.service.EmbeddingService;
import com.lovable.ai_service.validation.BuildValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RegenerationService {

    private final ImpactAnalyzer         impactAnalyzer;
    private final AiClientService        aiClientService;
    private final EmbeddingService       embeddingService;
    private final AiProgressProducer     progressProducer;
    private final ProjectSnapshot        projectSnapshot;
    private final DiffAwarePromptBuilder diffAwarePromptBuilder;
    private final BuildValidator         buildValidator;
    private final AdaptiveThresholdStore thresholdStore;

    private final ExecutorService pool;

    // ═════════════════════════════════════════════════════════════
    //  MAIN ENTRY POINT
    // ═════════════════════════════════════════════════════════════

    public List<GeneratedFile> regenerate(
            String projectId, String userPrompt,
            String sessionId, String framework
    ) {
        ImpactAnalyzer.AnalysisResult analysis =
                impactAnalyzer.analyze(projectId, userPrompt);

        log.info("[Regeneration] Intent={} files={} confidence={}",
                analysis.intent(), analysis.impactedFiles().size(),
                analysis.confidenceLabel());

        if (analysis.isAddFile()) {
            return handleAddFile(projectId, userPrompt, sessionId, framework, analysis);
        }

        if (analysis.impactedFiles().isEmpty()) {
            progressProducer.send(progress(projectId, sessionId, null,
                    "No impacted files detected. Skipping regeneration.", "DONE"));
            return List.of();
        }

        int snapshotSize = projectSnapshot.save(projectId);
        log.info("[Regeneration] Snapshot saved: {} files", snapshotSize);

        try {
            List<GeneratedFile> results =
                    doRegenerate(projectId, userPrompt, sessionId, framework, analysis);

            List<String> issues = buildValidator.validate(results, framework);
            if (!issues.isEmpty()) {
                log.warn("[Regeneration] {} issue(s) after regeneration — rolling back", issues.size());
                progressProducer.send(progress(projectId, sessionId, null,
                        "Build issues found — reverting to last stable version...", "ROLLING_BACK"));
                List<GeneratedFile> restored = projectSnapshot.rollback(projectId);
                if (!restored.isEmpty()) {
                    progressProducer.send(progress(projectId, sessionId, null,
                            "Rolled back successfully", "DONE"));
                    return restored;
                }
                log.error("[Regeneration] Rollback failed — no snapshot available");
            } else {
                thresholdStore.recordSuccess(projectId);
            }

            return results;

        } finally {
            projectSnapshot.clear(projectId);
        }
    }

    // ═════════════════════════════════════════════════════════════
    //  CORE REGENERATION
    // ═════════════════════════════════════════════════════════════

    private List<GeneratedFile> doRegenerate(
            String projectId, String userPrompt, String sessionId,
            String framework, ImpactAnalyzer.AnalysisResult analysis
    ) {
        Set<String> filesToProcess = analysis.impactedFiles();

        progressProducer.send(progress(projectId, sessionId, null,
                analysis.confidenceLabel() + " — updating "
                        + filesToProcess.size() + " file(s)", "PLANNING"));

        boolean useDiffAware = !analysis.isRestyle();

        // For RESTYLE: load shared context once (no more PSQLException risk)
        String sharedContext = null;
        if (!useDiffAware) {
            sharedContext = embeddingService.getProjectContext(projectId, filesToProcess);
        }
        final String finalSharedContext = sharedContext;

        List<CompletableFuture<GeneratedFile>> futures = new ArrayList<>();

        for (String filePath : filesToProcess) {
            futures.add(CompletableFuture.supplyAsync(() -> {
                progressProducer.send(progress(projectId, sessionId, filePath,
                        "Regenerating " + filePath, "GENERATING"));
                try {
                    GeneratedFile updated;
                    if (useDiffAware) {
                        // DiffAwarePromptBuilder now uses EmbeddingService.loadFileContent()
                        // — no more PSQLException, no more transaction poisoning
                        String diffPrompt = diffAwarePromptBuilder.buildDiffAwarePrompt(
                                projectId, filePath, userPrompt, framework);
                        updated = aiClientService.generateSingleFileWithPrompt(
                                diffPrompt, filePath, framework);
                    } else {
                        updated = aiClientService.generateSingleFile(
                                finalSharedContext, userPrompt, filePath,
                                filesToProcess, GenerationMode.REGENERATE, framework);
                    }
                    progressProducer.send(progress(projectId, sessionId, filePath,
                            "Finished " + filePath, "COMPLETED"));
                    return updated;
                } catch (Exception e) {
                    log.error("[Regeneration] Failed generating {}: {}", filePath, e.getMessage());
                    throw new RuntimeException("Generation failed for " + filePath, e);
                }
            }, pool));
        }

        List<GeneratedFile> results = futures.stream()
                .map(f -> {
                    try { return f.join(); }
                    catch (Exception e) {
                        log.error("[Regeneration] Future failed: {}", e.getMessage());
                        throw e;
                    }
                })
                .collect(Collectors.toList());

        // Store embeddings sequentially — avoids concurrent race conditions
        for (GeneratedFile file : results) {
            try {
                embeddingService.storeFileEmbeddings(projectId, file);
            } catch (Exception e) {
                log.error("[Regeneration] Embedding storage failed for {}: {}",
                        file.getPath(), e.getMessage());
            }
        }

        return results;
    }

    // ═════════════════════════════════════════════════════════════
    //  ADD FILE
    // ═════════════════════════════════════════════════════════════

    private List<GeneratedFile> handleAddFile(
            String projectId, String userPrompt, String sessionId,
            String framework, ImpactAnalyzer.AnalysisResult analysis
    ) {
        List<String> filePaths = extractAllRequestedPaths(userPrompt, analysis);
        log.info("[Regeneration] ADD_FILE: creating {} file(s): {}", filePaths.size(), filePaths);

        // No more try/catch around getProjectContext — VectorStore doesn't throw PSQLException
        String context   = embeddingService.getProjectContext(projectId, Set.of());
        String truncated = truncateContext(context, 10);

        List<GeneratedFile> results      = new ArrayList<>();
        List<String>        createdRoutes = new ArrayList<>();

        for (String filePath : filePaths) {
            String componentName = filePathToComponentName(filePath);
            progressProducer.send(progress(projectId, sessionId, filePath,
                    "Creating " + filePath + "...", "GENERATING"));
            try {
                String creationPrompt = """
                        Generate a NEW file for an existing project. Match the existing code style.

                        FRAMEWORK: %s
                        FILE: %s
                        COMPONENT: %s

                        USER REQUEST:
                        %s

                        EXISTING PROJECT STYLE (reference only):
                        %s

                        RULES:
                        - Use Tailwind CSS utility classes matching the existing style
                        - export default must be present
                        - Use lucide-react for icons
                        - Match the dark theme and Tailwind patterns from the context above

                        Return ONLY: { "path": "%s", "content": "..." }
                        """.formatted(framework, filePath, componentName,
                        userPrompt, truncated, filePath);

                GeneratedFile newFile = aiClientService.generateSingleFileWithPrompt(
                        creationPrompt, filePath, framework);

                results.add(newFile);
                embeddingService.storeFileEmbeddings(projectId, newFile);
                if (filePath.contains("/pages/")) createdRoutes.add(filePath);

                progressProducer.send(progress(projectId, sessionId, filePath,
                        "Created " + filePath, "COMPLETED"));

            } catch (Exception e) {
                log.error("[Regeneration] ADD_FILE failed for {}: {}", filePath, e.getMessage());
                progressProducer.send(progress(projectId, sessionId, filePath,
                        "Failed to create " + filePath, "FAILED"));
            }
        }

        // Update App.jsx with all new routes in a single AI call
        if (!createdRoutes.isEmpty()) {
            progressProducer.send(progress(projectId, sessionId, "src/App.jsx",
                    "Adding " + createdRoutes.size() + " route(s) to App.jsx...", "GENERATING"));

            StringBuilder routeInstruction = new StringBuilder(
                    "Add the following new Routes and imports. Keep all existing routes and imports unchanged.\n");
            for (String path : createdRoutes) {
                String name       = filePathToComponentName(path);
                String routePath  = toRoutePath(path);
                String importPath = "./" + path.replace("src/", "").replace(".jsx", "");
                routeInstruction.append(String.format(
                        "- Import %s from '%s' and add <Route path='%s' element={<%s />} />\n",
                        name, importPath, routePath, name));
            }

            try {
                // No more try/catch needed — DiffAwarePromptBuilder uses VectorStore now
                String appPrompt = diffAwarePromptBuilder.buildDiffAwarePrompt(
                        projectId, "src/App.jsx", routeInstruction.toString(), framework);
                GeneratedFile updatedApp = aiClientService.generateSingleFileWithPrompt(
                        appPrompt, "src/App.jsx", framework);
                results.add(updatedApp);
                embeddingService.storeFileEmbeddings(projectId, updatedApp);
                log.info("[Regeneration] App.jsx updated with {} route(s)", createdRoutes.size());
            } catch (Exception e) {
                log.warn("[Regeneration] Could not update App.jsx routing: {}", e.getMessage());
            }
        }

        return results;
    }

    // ═════════════════════════════════════════════════════════════
    //  MULTI-FILE EXTRACTION HELPERS
    // ═════════════════════════════════════════════════════════════

    private List<String> extractAllRequestedPaths(
            String userPrompt, ImpactAnalyzer.AnalysisResult analysis
    ) {
        String lower = userPrompt.toLowerCase();
        List<String> paths = new ArrayList<>();

        Pattern multiNoun = Pattern.compile(
                "\\b((?:\\w+(?:\\s*(?:,|and)\\s*))+)(page|component|screen|view|modal)",
                Pattern.CASE_INSENSITIVE);

        Matcher m = multiNoun.matcher(lower);
        if (m.find()) {
            String nounGroup = m.group(1);
            String fileType  = m.group(2);
            for (String noun : nounGroup.split("\\s*(?:,|and)\\s*")) {
                String trimmed = noun.trim();
                if (!trimmed.isEmpty()) paths.add(suggestNewFilePath(toPascalCase(trimmed), fileType));
            }
        }

        if (paths.isEmpty() && analysis.newFilePath() != null) paths.add(analysis.newFilePath());
        if (paths.isEmpty()) paths.add("src/components/NewComponent.jsx");

        log.debug("[Regeneration] Extracted {} path(s): {}", paths.size(), paths);
        return paths;
    }

    private String suggestNewFilePath(String name, String fileType) {
        return switch (fileType.toLowerCase()) {
            case "page", "screen", "view" ->
                    "src/pages/" + (name.endsWith("Page") ? name : name + "Page") + ".jsx";
            case "modal", "dialog" -> "src/components/modals/" + name + ".jsx";
            case "layout"          -> "src/layouts/" + name + ".jsx";
            default                -> "src/components/" + name + ".jsx";
        };
    }

    private String filePathToComponentName(String filePath) {
        return filePath
                .replaceAll(".*/", "")
                .replaceAll("\\.jsx$|\\.tsx$|\\.js$|\\.ts$", "");
    }

    private String toPascalCase(String input) {
        return Arrays.stream(input.trim().split("\\s+"))
                .filter(w -> !w.isEmpty())
                .map(w -> Character.toUpperCase(w.charAt(0))
                        + (w.length() > 1 ? w.substring(1).toLowerCase() : ""))
                .collect(Collectors.joining());
    }

    private String toRoutePath(String filePath) {
        return "/" + filePath
                .replaceAll(".*/(\\w+)\\.jsx$", "$1")
                .replaceAll("Page$|Screen$", "")
                .replaceAll("([A-Z])", "-$1")
                .toLowerCase()
                .replaceAll("^-", "");
    }

    private String truncateContext(String context, int maxLinesPerFile) {
        if (context == null || context.isBlank()) return "(no existing files)";
        StringBuilder sb = new StringBuilder();
        int fileCount = 0;
        for (String file : context.split("-----\n")) {
            if (file.isBlank() || fileCount >= 5) break;
            String[] lines = file.split("\n");
            int limit = Math.min(lines.length, maxLinesPerFile);
            for (int i = 0; i < limit; i++) sb.append(lines[i]).append("\n");
            if (lines.length > maxLinesPerFile) sb.append("...(truncated)\n");
            sb.append("-----\n");
            fileCount++;
        }
        return sb.toString();
    }

    private AiProgressEvent progress(String projectId, String sessionId,
                                     String filePath, String message, String status) {
        return AiProgressEvent.builder()
                .projectId(projectId).sessionId(sessionId)
                .filePath(filePath).message(message).status(status)
                .build();
    }
}