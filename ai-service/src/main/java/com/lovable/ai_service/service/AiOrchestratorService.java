package com.lovable.ai_service.service;

import com.lovable.ai_service.dto.*;
import com.lovable.ai_service.entity.ChatSession;
import com.lovable.ai_service.event.PreviewTriggerEvent;
import com.lovable.ai_service.producer.AiProgressProducer;
import com.lovable.ai_service.producer.AiResponseProducer;
import com.lovable.ai_service.producer.AiTokenProducer;
import com.lovable.ai_service.regeneration.RegenerationService;
import com.lovable.ai_service.validation.BuildAutoFixer;
import com.lovable.ai_service.validation.BuildValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiOrchestratorService {

    private final ChatSessionService  sessionService;
    private final ChatMessageService  messageService;
    private final AiClientService     aiClientService;
    private final AiResponseProducer  responseProducer;
    private final AiProgressProducer  progressProducer;
    private final AiTokenProducer     tokenProducer;
    private final EmbeddingService    embeddingService;
    private final RegenerationService regenerationService;
    private final PromptFactory       promptFactory;
    private final BuildValidator      buildValidator;
    private final BuildAutoFixer      buildAutoFixer;
    private final ApplicationEventPublisher publisher;

    // NOTE: @Transactional removed from process().
    // The old @Transactional caused the outer transaction to be marked
    // rollback-only whenever EmbeddingService threw a PSQLException
    // (Hibernate 7 + pgvector vector column deserialization).
    // Spring AI VectorStore manages its own transactions internally,
    // so no outer @Transactional is needed or safe here.
    public void process(AiRequestEvent event) {
        ChatSession session = sessionService.getOrCreate(event);
        String sessionId    = session.getId().toString();

        messageService.saveUserMessage(session.getId(), event.getPrompt());

        GenerationMode mode = "INITIAL_PROJECT".equals(event.getOperationType())
                ? GenerationMode.INITIAL
                : GenerationMode.REGENERATE;

        List<GeneratedFile> files = List.of();
        String framework = "unknown";

        try {
            sendThinking(event.getProjectId(), sessionId, "🤔 Understanding your request...");
            sleepQuietly(250);

            if (mode == GenerationMode.INITIAL) {
                sendThinking(event.getProjectId(), sessionId, "🧠 Analyzing requirements...");
                sleepQuietly(250);

                sendProgress(event.getProjectId(), sessionId, null,
                        "Planning project structure...", "PLANNING");
                sendThinking(event.getProjectId(), sessionId, "📐 Planning project structure...");

                ProjectSpec spec = aiClientService.planProject(event.getPrompt());
                framework = spec.getFramework();

                List<String> plannedFiles = sanitizePlannedFiles(spec.getFiles());
                if (plannedFiles.isEmpty())
                    throw new IllegalStateException("AI returned no planned files");

                sendProgress(event.getProjectId(), sessionId, null,
                        "Planned " + plannedFiles.size() + " files for " + framework, "PLANNING");
                sendThinking(event.getProjectId(), sessionId,
                        "📦 Planned " + plannedFiles.size() + " files using " + framework);
                sleepQuietly(200);

                files = generateInitialProjectInPhases(
                        event.getProjectId(), sessionId,
                        event.getPrompt(), plannedFiles, framework);

                files = validateAndFixBuild(
                        event.getProjectId(), sessionId,
                        event.getPrompt(), files, plannedFiles, framework);

            } else {
                framework = resolveFramework(event);

                sendProgress(event.getProjectId(), sessionId, null,
                        "Regenerating project...", "REGENERATING");
                sendThinking(event.getProjectId(), sessionId, "♻️ Regenerating requested files...");

                files = regenerationService.regenerate(
                        event.getProjectId(), event.getPrompt(), sessionId, framework);

                files = buildValidator.repairAll(files, framework);

                files = revalidateCssAfterRegeneration(
                        event.getProjectId(), sessionId,
                        event.getPrompt(), files, framework);
            }

            sendProgress(event.getProjectId(), sessionId, null, "Syncing preview...", "DONE");
            sendThinking(event.getProjectId(), sessionId, "✨ Finalizing project...");
            sleepQuietly(200);

            String summary = aiClientService.streamSummary(
                    event.getPrompt(), framework, files, mode,
                    event.getProjectId(), sessionId);

            messageService.saveAiMessage(session.getId(), summary);
            publisher.publishEvent(new PreviewTriggerEvent(event,session,files,framework));

        } catch (Exception e) {
            log.error("AI orchestration failed for project {}", event.getProjectId(), e);
            sendProgress(event.getProjectId(), sessionId, null,
                    "Generation failed: " + safeErrorMessage(e), "FAILED");

            String failureSummary = "Failed to generate the requested frontend.\nReason:\n- "
                    + safeErrorMessage(e);
            tokenProducer.send(AiTokenEvent.builder()
                    .projectId(event.getProjectId()).sessionId(sessionId)
                    .token(failureSummary).completed(false).build());
            tokenProducer.send(AiTokenEvent.builder()
                    .projectId(event.getProjectId()).sessionId(sessionId)
                    .token("").completed(true).build());

            messageService.saveAiMessage(session.getId(), failureSummary);
            throw e;
        }
    }

    // ═════════════════════════════════════════════════════════════
    //  GENERATE INITIAL PROJECT IN PHASES
    // ═════════════════════════════════════════════════════════════

    private List<GeneratedFile> generateInitialProjectInPhases(
            String projectId, String sessionId,
            String userPrompt, List<String> plannedFiles, String framework
    ) {
        List<String> orderedFiles = promptFactory.sortFilesForGeneration(plannedFiles);
        List<List<String>> phases = buildGenerationPhases(orderedFiles);
        List<GeneratedFile> allFiles = new ArrayList<>();
        String cssEntryPath = promptFactory.getCssEntryPath(framework);

        for (int i = 0; i < phases.size(); i++) {
            List<String> phase = phases.get(i);
            if (i == 0)      sendThinking(projectId, sessionId, "🧩 Structuring project foundation...");
            else if (i == 1) sendThinking(projectId, sessionId, "🏗 Building core app structure...");
            else             sendThinking(projectId, sessionId, "🎨 Building UI pages and components...");
            sleepQuietly(150);

            for (String filePath : phase) {
                try {
                    sendProgress(projectId, sessionId, filePath, "Generating " + filePath, "GENERATING");
                    sendThinking(projectId, sessionId, "⚡ Generating " + filePath + "...");

                    GeneratedFile file;

                    if (filePath.equals(cssEntryPath)) {
                        // CSS audit prompt — only pass JSX files, cap at 10
                        List<GeneratedFile> jsxFiles = allFiles.stream()
                                .filter(f -> f.getPath().endsWith(".jsx")
                                        || f.getPath().endsWith(".tsx")
                                        || f.getPath().endsWith(".vue"))
                                .limit(10)
                                .toList();
                        long jsxCount = jsxFiles.size();
                        sendThinking(projectId, sessionId,
                                "🎨 Building Tailwind CSS from " + jsxCount + " component files...");
                        file = aiClientService.generateCssFile(jsxFiles, userPrompt, framework);
                    } else {
                        String context = buildGenerationContext(framework, orderedFiles, allFiles);
                        file = aiClientService.generateSingleFile(
                                context, userPrompt, filePath,
                                Set.copyOf(orderedFiles), GenerationMode.INITIAL, framework);
                    }

                    if (file == null || file.getContent() == null || file.getContent().isBlank())
                        throw new IllegalStateException("Generated empty file for " + filePath);

                    allFiles.add(file);
                    sendProgress(projectId, sessionId, filePath, "Finished " + filePath, "COMPLETED");
                    sendThinking(projectId, sessionId, "✅ Finished " + filePath);
                    sleepQuietly(120);

                    // Store embeddings — non-fatal if Ollama OOMs on a chunk
                    try {
                        embeddingService.storeFileEmbeddings(projectId, file);
                    } catch (Exception e) {
                        log.warn("⚠️ Embedding failed for {} — skipping: {}", filePath, e.getMessage());
                    }

                } catch (Exception e) {
                    sendProgress(projectId, sessionId, filePath, "Failed " + filePath, "FAILED");
                    sendThinking(projectId, sessionId, "❌ Failed " + filePath);
                    throw new RuntimeException(e);
                }
            }
        }

        return allFiles;
    }

    // ═════════════════════════════════════════════════════════════
    //  VALIDATE AND FIX BUILD
    // ═════════════════════════════════════════════════════════════

    private List<GeneratedFile> validateAndFixBuild(
            String projectId, String sessionId,
            String userPrompt, List<GeneratedFile> files,
            List<String> plannedFiles, String framework
    ) {
        sendThinking(projectId, sessionId, "🔍 Validating Tailwind setup and build...");
        sleepQuietly(200);

        List<String> issues = buildValidator.validate(files, framework);

        if (issues.isEmpty()) {
            sendThinking(projectId, sessionId, "✅ Build validation passed");
            return files;
        }

        sendThinking(projectId, sessionId,
                "⚠️ Found " + issues.size() + " issue(s). Auto-fixing...");
        sleepQuietly(200);
        log.warn("⚠️ Build issues: {}", issues);

        List<GeneratedFile> fixedFiles = new ArrayList<>(files);

        for (String issue : issues) {
            sendThinking(projectId, sessionId, "🛠 Fixing: "
                    + issue.substring(0, Math.min(80, issue.length())));
            GeneratedFile fixed = buildAutoFixer.fix(issue, fixedFiles, userPrompt, framework);
            if (fixed != null) {
                fixedFiles.removeIf(f -> f.getPath().equals(fixed.getPath()));
                fixedFiles.add(fixed);
                sendThinking(projectId, sessionId, "✅ Fixed: " + fixed.getPath());
            }
        }

        List<String> remaining = buildValidator.validate(fixedFiles, framework);
        if (!remaining.isEmpty()) {
            log.error("❌ Still failing after auto-fix: {}", remaining);
            sendThinking(projectId, sessionId, "❌ Unfixed issues: " + remaining);
        } else {
            sendThinking(projectId, sessionId, "🎉 All issues fixed successfully");
        }

        return fixedFiles;
    }

    // ═════════════════════════════════════════════════════════════
    //  RE-VALIDATE CSS AFTER REGENERATION
    // ═════════════════════════════════════════════════════════════

    private List<GeneratedFile> revalidateCssAfterRegeneration(
            String projectId, String sessionId,
            String userPrompt, List<GeneratedFile> files, String framework
    ) {
        sendThinking(projectId, sessionId, "🔍 Re-validating Tailwind CSS after regeneration...");

        String cssPath = promptFactory.getCssEntryPath(framework);

        GeneratedFile cssFile = files.stream()
                .filter(f -> f.getPath().equals(cssPath))
                .findFirst().orElse(null);

        if (cssFile == null) {
            log.warn("⚠️ CSS entry file missing after regeneration: {}", cssPath);
            sendThinking(projectId, sessionId, "🎨 Rebuilding CSS entry file...");

            // Only pass JSX files, cap at 10 — prevents context length errors
            List<GeneratedFile> jsxFiles = files.stream()
                    .filter(f -> f.getPath().endsWith(".jsx")
                            || f.getPath().endsWith(".tsx")
                            || f.getPath().endsWith(".vue"))
                    .limit(10)
                    .toList();

            GeneratedFile newCss = aiClientService.generateCssFile(jsxFiles, userPrompt, framework);
            if (newCss != null) {
                List<GeneratedFile> updated = new ArrayList<>(files);
                updated.add(newCss);
                sendThinking(projectId, sessionId, "✅ CSS entry file restored: " + cssPath);
                return updated;
            }
            return files;
        }

        GeneratedFile repairedCss = buildValidator.repairFile(cssFile, framework);
        if (!repairedCss.getContent().equals(cssFile.getContent())) {
            log.info("⚠️ CSS entry file repaired after regeneration: {}", cssPath);
            sendThinking(projectId, sessionId, "✅ Tailwind directive fixed in " + cssPath);
            List<GeneratedFile> updated = new ArrayList<>(files);
            updated.removeIf(f -> f.getPath().equals(cssPath));
            updated.add(repairedCss);
            return updated;
        }

        sendThinking(projectId, sessionId, "✅ Tailwind CSS looks good after regeneration");
        return files;
    }

    // ═════════════════════════════════════════════════════════════
    //  PHASE BUILDER
    // ═════════════════════════════════════════════════════════════

    private List<List<String>> buildGenerationPhases(List<String> files) {
        List<String> phase1 = new ArrayList<>();
        List<String> phase2 = new ArrayList<>();
        List<String> phase3 = new ArrayList<>();
        List<String> phase4 = new ArrayList<>();

        for (String file : files) {
            String n = file.toLowerCase(Locale.ROOT);
            if (n.endsWith(".css") || n.endsWith(".scss")) {
                phase4.add(file);
            } else if (n.equals("package.json")
                    || n.equals("vite.config.js") || n.equals("vite.config.ts")
                    || n.equals("index.html")
                    || n.equals("next.config.js") || n.equals("next.config.mjs")
                    || n.equals("angular.json") || n.equals("tsconfig.json")
                    || n.equals("tailwind.config.js") || n.equals("postcss.config.js")) {
                phase1.add(file);
            } else if (n.contains("main.")
                    || n.endsWith("/layout.js") || n.endsWith("/layout.tsx")
                    || n.endsWith("/page.js")   || n.endsWith("/page.tsx")
                    || n.endsWith("/app.jsx")   || n.endsWith("/app.tsx")
                    || n.endsWith("/app.vue")) {
                phase2.add(file);
            } else {
                phase3.add(file);
            }
        }

        List<List<String>> phases = new ArrayList<>();
        if (!phase1.isEmpty()) phases.add(phase1);
        if (!phase2.isEmpty()) phases.add(phase2);
        if (!phase3.isEmpty()) phases.add(phase3);
        if (!phase4.isEmpty()) phases.add(phase4);
        return phases;
    }

    // ═════════════════════════════════════════════════════════════
    //  HELPERS
    // ═════════════════════════════════════════════════════════════

    private List<String> sanitizePlannedFiles(List<String> files) {
        if (files == null) return List.of();
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (String f : files) if (f != null && !f.isBlank()) seen.add(f.trim());
        return new ArrayList<>(seen);
    }

    private String buildGenerationContext(
            String framework, List<String> plannedFiles, List<GeneratedFile> generatedFiles
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("FRAMEWORK: ").append(framework).append("\n");
        sb.append("PLANNED FILES:\n");
        for (String f : plannedFiles) sb.append("- ").append(f).append("\n");
        sb.append("\nGENERATED FILES SO FAR:\n");

        if (generatedFiles.isEmpty()) {
            sb.append("(none)\n");
            return sb.toString();
        }

        // Cap: 8 files max, 15 lines each — prevents context length errors
        int fileLimit = Math.min(generatedFiles.size(), 8);
        for (int i = 0; i < fileLimit; i++) {
            GeneratedFile f = generatedFiles.get(i);
            sb.append("FILE: ").append(f.getPath()).append("\n");
            String[] lines = f.getContent().split("\n");
            int lineLimit = Math.min(lines.length, 15);
            for (int j = 0; j < lineLimit; j++) sb.append(lines[j]).append("\n");
            if (lines.length > 15) sb.append("// ... truncated\n");
            sb.append("-----\n");
        }
        if (generatedFiles.size() > 8)
            sb.append("// ... and ").append(generatedFiles.size() - 8).append(" more files omitted\n");

        return sb.toString();
    }

    private void sendProgress(String projectId, String sessionId,
                              String filePath, String message, String status) {
        progressProducer.send(AiProgressEvent.builder()
                .projectId(projectId).sessionId(sessionId)
                .filePath(filePath).message(message).status(status).build());
    }

    private void sendThinking(String projectId, String sessionId, String message) {
        tokenProducer.send(AiTokenEvent.builder()
                .projectId(projectId).sessionId(sessionId)
                .token(message + "\n").completed(false).build());
    }

    private String safeErrorMessage(Exception e) {
        String msg = e.getMessage();
        return (msg == null || msg.isBlank()) ? "Unknown error" : msg;
    }

    private String resolveFramework(AiRequestEvent event) {
        if (event.getFramework() != null && !event.getFramework().isBlank())
            return event.getFramework();
        return promptFactory.detectFramework(event.getPrompt());
    }

    private void sleepQuietly(long millis) {
        try { Thread.sleep(millis); }
        catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
    }
}