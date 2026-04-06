package com.lovable.ai_service.regeneration;

import com.lovable.ai_service.dto.*;
import com.lovable.ai_service.producer.AiProgressProducer;
import com.lovable.ai_service.service.*;
import com.lovable.ai_service.validation.BuildValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RegenerationService {

    private final ImpactAnalyzer impactAnalyzer;
    private final AiClientService aiClientService;
    private final EmbeddingService embeddingService;
    private final AiProgressProducer progressProducer;
    private final ProjectSnapshot projectSnapshot;
    private final DiffAwarePromptBuilder diffAwarePromptBuilder;
    private final BuildValidator buildValidator;
    private final AdaptiveThresholdStore thresholdStore;
    private final ExecutorService pool;
    private final MultiCandidateGenerationService multiCandidateGenerationService;
    private final CandidateJudgeService candidateJudgeService;

    public List<GeneratedFile> regenerate(
            String projectId,
            String userPrompt,
            String sessionId,
            String framework
    ) {
        ImpactAnalyzer.AnalysisResult analysis = impactAnalyzer.analyze(projectId, userPrompt);

        log.info("[Regeneration] intent={} impacted={} confidence={}",
                analysis.intent(), analysis.impactedFiles().size(), analysis.confidenceLabel());

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
            List<GeneratedFile> results = doRegenerate(projectId, userPrompt, sessionId, framework, analysis);

            List<String> issues = buildValidator.validate(results, framework);

            if (!issues.isEmpty()) {

                log.warn("[Regeneration] {} issue(s) detected — attempting self-healing", issues.size());

                progressProducer.send(progress(projectId, sessionId, null,
                        "Fixing build issues automatically...", "SELF_HEALING"));

                try {
                    List<GeneratedFile> healed = attemptSelfHealing(
                            projectId,
                            sessionId,
                            framework,
                            issues
                    );

                    List<String> recheck = buildValidator.validate(healed, framework);

                    if (recheck.isEmpty()) {
                        log.info("[Regeneration] Self-healing successful");

                        progressProducer.send(progress(projectId, sessionId, null,
                                "Auto-fix successful", "DONE"));

                        thresholdStore.recordSuccess(projectId);
                        return healed;
                    }

                    log.warn("[Regeneration] Self-healing still has {} issues", recheck.size());

                } catch (Exception e) {
                    log.warn("[Regeneration] Self-healing failed: {}", e.getMessage());
                }

                // 🔴 FALLBACK → rollback
                log.warn("[Regeneration] Rolling back to last stable state");

                progressProducer.send(progress(projectId, sessionId, null,
                        "Reverting to last stable version...", "ROLLING_BACK"));

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

    private List<GeneratedFile> attemptSelfHealing(
            String projectId,
            String sessionId,
            String framework,
            List<String> issues
    ) {
        String prompt = """
        Fix build issues in this project.

        ISSUES:
        %s

        Fix only necessary files.
        Do not break working code.
        Return JSON.
        """.formatted(String.join("\n", issues));

        ImpactAnalyzer.AnalysisResult analysis =
                impactAnalyzer.analyze(projectId, prompt);

        return doRegenerate(
                projectId,
                prompt,
                sessionId,
                framework,
                analysis
        );
    }

    private List<GeneratedFile> doRegenerate(
            String projectId,
            String userPrompt,
            String sessionId,
            String framework,
            ImpactAnalyzer.AnalysisResult analysis
    ) {
        Set<String> filesToProcess = analysis.impactedFiles();

        progressProducer.send(progress(projectId, sessionId, null,
                analysis.confidenceLabel() + " — updating " + filesToProcess.size() + " file(s)", "PLANNING"));

        boolean useDiffAware = !analysis.isRestyle();
        String sharedContext = null;

        if (!useDiffAware) {
            sharedContext = embeddingService.getProjectContext(projectId, filesToProcess);
        }

        final String finalSharedContext = sharedContext;
        Map<String, String> existingContent = embeddingService.loadAllFileContents(projectId);

        List<GeneratedFile> existingFiles = existingContent.entrySet().stream()
                .map(e -> GeneratedFile.builder().path(e.getKey()).content(e.getValue()).build())
                .collect(Collectors.toList());

        List<CompletableFuture<GeneratedFile>> futures = new ArrayList<>();

        for (String filePath : filesToProcess) {
            futures.add(CompletableFuture.supplyAsync(() -> {
                progressProducer.send(progress(projectId, sessionId, filePath,
                        "Regenerating " + filePath, "GENERATING"));

                try {
                    GeneratedFile updated;

                    if (useDiffAware) {
                        String diffPrompt = diffAwarePromptBuilder.buildDiffAwarePrompt(
                                projectId, filePath, userPrompt, framework);
                        updated = aiClientService.generateSingleFileWithPrompt(diffPrompt, filePath, framework);
                    } else {
                        PromptContext ctx = PromptContext.builder()
                                .projectId(projectId)
                                .sessionId(sessionId)
                                .userPrompt(userPrompt)
                                .mode(GenerationMode.REGENERATE)
                                .framework(framework)
                                .rawContext(finalSharedContext)
                                .existingFiles(existingFiles)
                                .targetFiles(filesToProcess)
                                .impactedFiles(filesToProcess)
                                .build();

                        if (useDiffAware) {
                            String diffPrompt = diffAwarePromptBuilder.buildDiffAwarePrompt(
                                    projectId, filePath, userPrompt, framework);
                            updated = aiClientService.generateSingleFileWithPrompt(diffPrompt, filePath, framework);

                        } else {
                            PromptContext ct = PromptContext.builder()
                                    .projectId(projectId)
                                    .sessionId(sessionId)
                                    .userPrompt(userPrompt)
                                    .mode(GenerationMode.REGENERATE)
                                    .framework(framework)
                                    .rawContext(finalSharedContext)
                                    .existingFiles(existingFiles)
                                    .targetFiles(filesToProcess)
                                    .impactedFiles(filesToProcess)
                                    .build();

                            if (shouldUseCandidates(filePath)) {

                                List<GenerationCandidate> candidates =
                                        multiCandidateGenerationService.generateCandidates(ct, filePath, 2);

                                GenerationCandidate winner =
                                        candidateJudgeService.judgeAndSelect(ct, candidates);

                                updated = winner.getFile();

                            } else {
                                updated = aiClientService.generateSingleFile(ctx, filePath);
                            }
                        }                    }

                    progressProducer.send(progress(projectId, sessionId, filePath,
                            "Finished " + filePath, "COMPLETED"));
                    return updated;

                } catch (Exception e) {
                    log.error("[Regeneration] Failed generating {}: {}", filePath, e.getMessage());
                    throw new RuntimeException("Generation failed for " + filePath, e);
                }
            }, pool));
        }

        List<GeneratedFile> results = futures.stream().map(CompletableFuture::join).collect(Collectors.toList());

        for (GeneratedFile file : results) {
            try {
                embeddingService.storeFileEmbeddings(projectId, file);
            } catch (Exception e) {
                log.error("[Regeneration] Embedding storage failed for {}: {}", file.getPath(), e.getMessage());
            }
        }

        return results;
    }

    private List<GeneratedFile> handleAddFile(
            String projectId,
            String userPrompt,
            String sessionId,
            String framework,
            ImpactAnalyzer.AnalysisResult analysis
    ) {
        List<ImpactAnalyzer.RequestedArtifact> artifacts = analysis.requestedArtifacts();
        if (artifacts == null || artifacts.isEmpty()) {
            progressProducer.send(progress(projectId, sessionId, null,
                    "Could not determine files to create.", "FAILED"));
            return List.of();
        }

        String context = embeddingService.getProjectContext(projectId, Set.of());
        String truncated = truncateContext(context, 10);

        List<GeneratedFile> results = new ArrayList<>();
        List<ImpactAnalyzer.RequestedArtifact> createdPages = new ArrayList<>();

        for (ImpactAnalyzer.RequestedArtifact artifact : artifacts) {
            String filePath = artifact.filePath();
            String componentName = artifact.componentName();

            progressProducer.send(progress(projectId, sessionId, filePath,
                    "Creating " + filePath + "...", "GENERATING"));

            try {
                String creationPrompt = """
                    Generate a NEW file for an existing project. Match the existing code style.

                    FRAMEWORK: %s
                    FILE: %s
                    COMPONENT: %s
                    ARTIFACT TYPE: %s
                    USER REQUEST:
                    %s

                    EXISTING PROJECT STYLE:
                    %s

                    RULES:
                    - Create exactly the requested file
                    - Use the exact component name: %s
                    - Match existing style and folder conventions
                    - Use Tailwind utility classes
                    - export default must be present
                    - Return ONLY valid JSON

                    OUTPUT:
                    { "path": "%s", "content": "..." }
                    """.formatted(
                        framework,
                        filePath,
                        componentName,
                        artifact.type(),
                        userPrompt,
                        truncated,
                        componentName,
                        filePath
                );

                PromptContext addCtx = PromptContext.builder()
                        .projectId(projectId)
                        .sessionId(sessionId)
                        .userPrompt(userPrompt)
                        .mode(GenerationMode.REGENERATE)
                        .framework(framework)
                        .existingFiles(results)
                        .build();

                GeneratedFile newFile;

                if ("page".equalsIgnoreCase(artifact.type()) || "layout".equalsIgnoreCase(artifact.type())) {
                    List<GenerationCandidate> candidates =
                            multiCandidateGenerationService.generateCandidates(addCtx, filePath, 2);

                    GenerationCandidate winner =
                            candidateJudgeService.judgeAndSelect(addCtx, candidates);
                    newFile = winner.getFile();
                } else {
                    newFile = aiClientService.generateSingleFileWithPrompt(creationPrompt, filePath, framework);
                }
                results.add(newFile);
                embeddingService.storeFileEmbeddings(projectId, newFile);

                if ("page".equalsIgnoreCase(artifact.type())
                        || "screen".equalsIgnoreCase(artifact.type())
                        || "view".equalsIgnoreCase(artifact.type())) {
                    createdPages.add(artifact);
                }

                progressProducer.send(progress(projectId, sessionId, filePath,
                        "Created " + filePath, "COMPLETED"));

            } catch (Exception e) {
                log.error("[Regeneration] ADD_FILE failed for {}: {}", filePath, e.getMessage());
                progressProducer.send(progress(projectId, sessionId, filePath,
                        "Failed to create " + filePath, "FAILED"));
            }
        }

        if (!createdPages.isEmpty()) {
            updateAppRouting(projectId, sessionId, framework, createdPages, results);
        }

        return results;
    }

    private void updateAppRouting(
            String projectId,
            String sessionId,
            String framework,
            List<ImpactAnalyzer.RequestedArtifact> createdPages,
            List<GeneratedFile> results
    ) {
        String appFile = resolveAppRouterFile(projectId);
        if (appFile == null) return;

        progressProducer.send(progress(projectId, sessionId, appFile,
                "Adding " + createdPages.size() + " route(s)...", "GENERATING"));

        StringBuilder routeInstruction = new StringBuilder("""
            Add imports and routes for these new pages.
            Keep all existing imports, routes, wrappers, layouts, and logic unchanged.
            Do not remove any current routes.
            """ + "\n");

        for (ImpactAnalyzer.RequestedArtifact artifact : createdPages) {
            String componentName = artifact.componentName();
            String routePath = toRoutePathFromComponent(componentName);
            String importPath = "./" + artifact.filePath()
                    .replaceFirst("^src/", "")
                    .replaceAll("\\.jsx$|\\.tsx$", "");

            routeInstruction.append("- Import ").append(componentName)
                    .append(" from '").append(importPath).append("'\n");
            routeInstruction.append("- Add route <Route path=\"").append(routePath)
                    .append("\" element={<").append(componentName).append(" />} />\n");
        }

        try {
            String appPrompt = diffAwarePromptBuilder.buildDiffAwarePrompt(
                    projectId, appFile, routeInstruction.toString(), framework);

            GeneratedFile updatedApp = aiClientService.generateSingleFileWithPrompt(appPrompt, appFile, framework);
            results.add(updatedApp);
            embeddingService.storeFileEmbeddings(projectId, updatedApp);

            progressProducer.send(progress(projectId, sessionId, appFile,
                    "Updated routing successfully", "COMPLETED"));

        } catch (Exception e) {
            log.warn("[Regeneration] Could not update routing in {}: {}", appFile, e.getMessage());
            progressProducer.send(progress(projectId, sessionId, appFile,
                    "Failed to update routing", "FAILED"));
        }
    }

    private String resolveAppRouterFile(String projectId) {
        Map<String, String> all = embeddingService.loadAllFileContents(projectId);
        List<String> candidates = List.of("src/App.jsx", "src/App.tsx", "src/app.jsx", "src/app.tsx");
        for (String candidate : candidates) {
            if (all.containsKey(candidate)) return candidate;
        }
        return null;
    }

    private String toRoutePathFromComponent(String componentName) {
        String base = componentName.replaceAll("Page$|Screen$|View$", "");
        return "/" + base.replaceAll("([a-z])([A-Z])", "$1-$2").toLowerCase();
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

    private AiProgressEvent progress(String projectId, String sessionId, String filePath, String message, String status) {
        return AiProgressEvent.builder()
                .projectId(projectId)
                .sessionId(sessionId)
                .filePath(filePath)
                .message(message)
                .status(status)
                .build();
    }
    private boolean shouldUseCandidates(String filePath) {
        String f = filePath.toLowerCase();
        return f.contains("page")
                || f.contains("home")
                || f.contains("dashboard")
                || f.contains("app")
                || f.contains("layout");
    }
}