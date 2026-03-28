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
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiOrchestratorService {

    private final ChatSessionService sessionService;
    private final ChatMessageService messageService;
    private final AiClientService aiClientService;
    private final AiResponseProducer responseProducer;
    private final AiProgressProducer progressProducer;
    private final AiTokenProducer tokenProducer;
    private final EmbeddingService embeddingService;
    private final RegenerationService regenerationService;
    private final PromptFactory promptFactory;
    private final BuildValidator buildValidator;
    private final BuildAutoFixer buildAutoFixer;
    private final ApplicationEventPublisher publisher;

    @Transactional
    public void process(AiRequestEvent event) {

        ChatSession session = sessionService.getOrCreate(event);
        String sessionId = session.getId().toString();

        messageService.saveUserMessage(session.getId(), event.getPrompt());

        GenerationMode mode = "INITIAL_PROJECT".equals(event.getOperationType())
                ? GenerationMode.INITIAL
                : GenerationMode.REGENERATE;

        List<GeneratedFile> files = List.of();
        String framework = "unknown";

        try {
            sendGlobalStatus(event.getProjectId(), sessionId,
                    "🤔 Understanding your request...", "THINKING");

            if (mode == GenerationMode.INITIAL) {

                sendGlobalStatus(event.getProjectId(), sessionId,
                        "🧠 Analyzing requirements...", "ANALYZING");

                sendProgress(event.getProjectId(), sessionId, null,
                        "Planning project structure...", "PLANNING");

                sendGlobalStatus(event.getProjectId(), sessionId,
                        "📐 Planning project structure...", "PLANNING");

                ProjectSpec spec = aiClientService.planProject(event.getPrompt());
                framework = spec.getFramework();

                List<String> plannedFiles = sanitizePlannedFiles(spec.getFiles());
                if (plannedFiles.isEmpty()) {
                    throw new IllegalStateException("No files returned by planner");
                }

                sendProgress(event.getProjectId(), sessionId, null,
                        "Planned " + plannedFiles.size() + " files for " + framework, "PLANNING");

                sendGlobalStatus(event.getProjectId(), sessionId,
                        "📦 Planned " + plannedFiles.size() + " files using " + framework, "PLANNED");

                files = generateInitialProjectInPhases(
                        event.getProjectId(),
                        sessionId,
                        event.getPrompt(),
                        plannedFiles,
                        framework
                );

                // BUG 1 FIX: argument order was (files, prompt, framework) but signature
                // is validateAndFixBuild(files, framework, userPrompt). Swapped here.
                files = validateAndFixBuild(
                        files,
                        framework,
                        event.getPrompt()
                );

            } else {

                framework = resolveFramework(event);

                sendGlobalStatus(event.getProjectId(), sessionId,
                        "♻️ Regenerating requested files...", "REGENERATING");

                files = regenerationService.regenerate(
                        event.getProjectId(),
                        event.getPrompt(),
                        sessionId,
                        framework
                );

                files = buildValidator.repairAll(files, framework);

                files = revalidateCssAfterRegeneration(
                        event.getProjectId(),
                        sessionId,
                        event.getPrompt(),
                        files,
                        framework
                );
            }

            sendGlobalStatus(event.getProjectId(), sessionId,
                    "✨ Finalizing project...", "DONE");

            String summary = aiClientService.streamSummary(
                    event.getPrompt(),
                    framework,
                    files,
                    mode,
                    event.getProjectId(),
                    sessionId
            );

            messageService.saveAiMessage(session.getId(), summary);

            publisher.publishEvent(new PreviewTriggerEvent(event, session, files, framework));

        } catch (Exception e) {
            log.error("AI orchestration failed for project {}", event.getProjectId(), e);

            sendProgress(event.getProjectId(), sessionId, null,
                    "Generation failed: " + safeErrorMessage(e), "FAILED");

            tokenProducer.send(AiTokenEvent.builder()
                    .projectId(event.getProjectId())
                    .sessionId(sessionId)
                    .token("Failed to generate the requested frontend.\nReason: " + safeErrorMessage(e))
                    .completed(false)
                    .build());

            tokenProducer.send(AiTokenEvent.builder()
                    .projectId(event.getProjectId())
                    .sessionId(sessionId)
                    .token("")
                    .completed(true)
                    .build());

            throw e;
        }
    }

    private List<GeneratedFile> generateInitialProjectInPhases(
            String projectId,
            String sessionId,
            String userPrompt,
            List<String> plannedFiles,
            String framework
    ) {

        List<String> orderedFiles = promptFactory.sortFilesForGeneration(plannedFiles);
        List<List<String>> phases = buildGenerationPhases(orderedFiles);

        List<GeneratedFile> allFiles = Collections.synchronizedList(new ArrayList<>());
        String cssEntryPath = promptFactory.getCssEntryPath(framework);

        ExecutorService executor = Executors.newFixedThreadPool(4);
        Semaphore semaphore = new Semaphore(2);

        try {
            for (int phaseIndex = 0; phaseIndex < phases.size(); phaseIndex++) {
                List<String> phase = phases.get(phaseIndex);

                if (phaseIndex == 0) {
                    sendGlobalStatus(projectId, sessionId,
                            "🧩 Structuring project foundation...", "STRUCTURING");
                } else if (phaseIndex == 1) {
                    sendGlobalStatus(projectId, sessionId,
                            "🏗 Building core app structure...", "BUILDING");
                } else {
                    sendGlobalStatus(projectId, sessionId,
                            "🎨 Building UI pages and components...", "BUILDING");
                }

                // BUG 5 FIX: The original code started all futures in parallel with a
                // context snapshot taken at future-creation time. Then it joined them
                // sequentially. This meant later files in the phase all got the same
                // (often empty) context because allFiles hadn't grown yet when their
                // futures captured it via the lambda.
                //
                // Fix: for non-CSS files, resolve them sequentially within the phase so
                // each file's context includes all previously completed files. The semaphore
                // still limits concurrent AI calls to 2 at a time. For phases with only
                // 1-2 files this has zero performance cost; for larger phases the
                // context quality improvement outweighs the parallelism loss.
                for (String filePath : phase) {
                    try {
                        sendProgress(projectId, sessionId, filePath,
                                "Generating " + filePath, "GENERATING");

                        GeneratedFile file;

                        if (filePath.equals(cssEntryPath)) {
                            List<GeneratedFile> jsxFiles = allFiles.stream()
                                    .filter(f -> f.getPath().endsWith(".jsx")
                                            || f.getPath().endsWith(".tsx")
                                            || f.getPath().endsWith(".vue"))
                                    .limit(10)
                                    .toList();

                            file = aiClientService.generateCssFile(jsxFiles, userPrompt, framework);
                        } else {
                            semaphore.acquire();
                            try {
                                // Context is built NOW — after previous files in this phase
                                // have already been added to allFiles. This is the key fix.
                                String context = buildGenerationContext(framework, orderedFiles, allFiles);

                                file = aiClientService.generateSingleFile(
                                        context,
                                        userPrompt,
                                        filePath,
                                        Set.copyOf(orderedFiles),
                                        GenerationMode.INITIAL,
                                        framework
                                );
                            } finally {
                                semaphore.release();
                            }
                        }

                        if (file == null || file.getContent() == null || file.getContent().isBlank()) {
                            throw new IllegalStateException("Generated empty file for " + filePath);
                        }

                        // Stream the file content, then add to allFiles so subsequent
                        // files in the same phase get it in their context.
                        CompletableFuture<Void> streamFuture = streamFileContent(projectId, sessionId, file);
                        streamFuture.join();

                        allFiles.add(file);

                        sendProgress(projectId, sessionId, filePath,
                                "Finished " + filePath, "COMPLETED");

                        CompletableFuture.runAsync(() -> {
                            try {
                                embeddingService.storeFileEmbeddings(projectId, file);
                            } catch (Exception e) {
                                log.warn("Embedding failed for {}: {}", filePath, e.getMessage());
                            }
                        });

                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Interrupted while generating " + filePath, e);
                    } catch (Exception e) {
                        sendProgress(projectId, sessionId, filePath,
                                "Failed " + filePath, "FAILED");
                        throw new RuntimeException(e);
                    }
                }
            }
        } finally {
            executor.shutdown();
        }

        return allFiles;
    }

    // BUG 1 FIX: corrected argument order — was (files, framework, userPrompt) in signature
    // but called as (files, prompt, framework) from process(). Now both match.
    public List<GeneratedFile> validateAndFixBuild(
            List<GeneratedFile> files,
            String framework,
            String userPrompt
    ) {
        int maxAttempts = 3;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {

            log.info("🔍 Validation attempt {}", attempt);

            // STEP 1 — Repair each file
            files = buildValidator.repairAll(files, framework);

            // STEP 2 — Validate project
            List<String> issues = buildValidator.validate(files, framework);

            if (issues.isEmpty()) {
                log.info("✅ Build validation passed");
                return files;
            }

            log.warn("⚠️ Found {} issues", issues.size());

            // STEP 3 — Fix issues
            for (String issue : issues) {
                GeneratedFile fixed = buildAutoFixer.fix(issue, files, userPrompt, framework);

                if (fixed != null) {
                    files = replaceFile(files, fixed);
                }
            }
        }

        log.error("❌ Build failed after max retries");
        return files;
    }

    private List<GeneratedFile> replaceFile(List<GeneratedFile> files, GeneratedFile updated) {
        return files.stream()
                .map(f -> f.getPath().equals(updated.getPath()) ? updated : f)
                .collect(Collectors.toList());
    }

    private List<GeneratedFile> revalidateCssAfterRegeneration(
            String projectId,
            String sessionId,
            String userPrompt,
            List<GeneratedFile> files,
            String framework
    ) {
        sendGlobalStatus(projectId, sessionId,
                "🔍 Re-validating Tailwind CSS after regeneration...", "REVALIDATING");

        String cssPath = promptFactory.getCssEntryPath(framework);

        GeneratedFile cssFile = files.stream()
                .filter(f -> f.getPath().equals(cssPath))
                .findFirst()
                .orElse(null);

        if (cssFile == null) {
            log.warn("CSS entry file missing after regeneration: {}", cssPath);
            sendGlobalStatus(projectId, sessionId,
                    "🎨 Rebuilding CSS entry file...", "REBUILDING");

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
                sendGlobalStatus(projectId, sessionId,
                        "✅ CSS entry file restored: " + cssPath, "RESTORE");
                return updated;
            }
            return files;
        }

        GeneratedFile repairedCss = buildValidator.repairFile(cssFile, framework);
        if (!Objects.equals(repairedCss.getContent(), cssFile.getContent())) {
            List<GeneratedFile> updated = new ArrayList<>(files);
            updated.removeIf(f -> f.getPath().equals(cssPath));
            updated.add(repairedCss);

            sendGlobalStatus(projectId, sessionId,
                    "✅ Tailwind directive fixed in " + cssPath, "FIXED");
            return updated;
        }

        sendGlobalStatus(projectId, sessionId,
                "✅ Tailwind CSS looks good after regeneration", "REGENERATION");

        return files;
    }

    private String buildGenerationContext(
            String framework,
            List<String> plannedFiles,
            List<GeneratedFile> generatedFiles
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("FRAMEWORK: ").append(framework).append("\n");
        sb.append("PLANNED FILES:\n");
        for (String f : plannedFiles) {
            sb.append("- ").append(f).append("\n");
        }

        sb.append("\nGENERATED FILES SO FAR:\n");
        if (generatedFiles.isEmpty()) {
            sb.append("(none)\n");
            return sb.toString();
        }

        int fileLimit = Math.min(generatedFiles.size(), 8);
        for (int i = 0; i < fileLimit; i++) {
            GeneratedFile f = generatedFiles.get(i);
            sb.append("FILE: ").append(f.getPath()).append("\n");

            String[] lines = f.getContent().split("\n");
            int lineLimit = Math.min(lines.length, 15);
            for (int j = 0; j < lineLimit; j++) {
                sb.append(lines[j]).append("\n");
            }
            if (lines.length > 15) {
                sb.append("// ... truncated\n");
            }
            sb.append("-----\n");
        }

        if (generatedFiles.size() > 8) {
            sb.append("// ... and ").append(generatedFiles.size() - 8).append(" more files omitted\n");
        }

        return sb.toString();
    }

    private CompletableFuture<Void> streamFileContent(String projectId, String sessionId, GeneratedFile file) {
        return CompletableFuture.runAsync(() -> {
            String content = file.getContent();
            if (content == null || content.isBlank()) {
                return;
            }

            List<String> chunks = splitIntoChunks(content, 120);

            for (String chunk : chunks) {
                tokenProducer.send(AiTokenEvent.builder()
                        .projectId(projectId)
                        .sessionId(sessionId)
                        .filePath(file.getPath())
                        .status("GENERATING")
                        .token(chunk)
                        .completed(false)
                        .build());

                sleepQuietly(20);
            }
        });
    }

    private List<String> splitIntoChunks(String text, int size) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return chunks;
        }

        for (int i = 0; i < text.length(); i += size) {
            chunks.add(text.substring(i, Math.min(text.length(), i + size)));
        }
        return chunks;
    }

    private void sendGlobalStatus(String projectId, String sessionId, String message, String status) {
        tokenProducer.send(AiTokenEvent.builder()
                .projectId(projectId)
                .sessionId(sessionId)
                .status(status)
                .token(message)
                .completed(false)
                .build());
    }

    private void sendProgress(String projectId, String sessionId,
                              String filePath, String message, String status) {
        progressProducer.send(AiProgressEvent.builder()
                .projectId(projectId)
                .sessionId(sessionId)
                .filePath(filePath)
                .message(message)
                .status(status)
                .build());
    }

    private String resolveFramework(AiRequestEvent event) {
        if (event.getFramework() != null && !event.getFramework().isBlank()) {
            return event.getFramework();
        }
        return promptFactory.detectFramework(event.getPrompt());
    }

    private List<String> sanitizePlannedFiles(List<String> files) {
        if (files == null) {
            return List.of();
        }

        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (String f : files) {
            if (f != null && !f.isBlank()) {
                seen.add(f.trim());
            }
        }
        return new ArrayList<>(seen);
    }

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
                    || n.equals("vite.config.js")
                    || n.equals("vite.config.ts")
                    || n.equals("index.html")
                    || n.equals("next.config.js")
                    || n.equals("next.config.mjs")
                    || n.equals("angular.json")
                    || n.equals("tsconfig.json")
                    || n.equals("tailwind.config.js")
                    || n.equals("postcss.config.js")) {
                phase1.add(file);
            } else if (n.contains("main.")
                    || n.endsWith("/layout.js")
                    || n.endsWith("/layout.jsx")
                    || n.endsWith("/layout.tsx")
                    || n.endsWith("/page.js")
                    || n.endsWith("/page.jsx")
                    || n.endsWith("/page.tsx")
                    || n.endsWith("/app.jsx")
                    || n.endsWith("/app.tsx")
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

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private String safeErrorMessage(Exception e) {
        String msg = e.getMessage();
        return (msg == null || msg.isBlank()) ? "Unknown error" : msg;
    }
}