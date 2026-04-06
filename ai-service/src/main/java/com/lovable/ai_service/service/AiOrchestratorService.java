package com.lovable.ai_service.service;

import com.lovable.ai_service.dependency.PackageJsonEnricherService;
import com.lovable.ai_service.dto.*;
import com.lovable.ai_service.entity.ChatSession;
import com.lovable.ai_service.producer.AiPartialProducer;
import com.lovable.ai_service.producer.AiProgressProducer;
import com.lovable.ai_service.producer.AiResponseProducer;
import com.lovable.ai_service.producer.AiTokenProducer;
import com.lovable.ai_service.prompt.PromptFactory;
import com.lovable.ai_service.regeneration.RegenerationService;
import com.lovable.ai_service.validation.BuildAutoFixer;
import com.lovable.ai_service.validation.BuildValidator;
import com.lovable.ai_service.validation.fixer.PackageJsonSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiOrchestratorService {

    private static final int GENERATION_THREADS = 4;
    private static final int MAX_CONCURRENT_LLM_CALLS = 2;
    private static final int VALIDATION_MAX_ATTEMPTS = 2;
    private static final int STREAM_CHUNK_SIZE = 140;
    private static final int STREAM_DELAY_MS = 12;

    private final ChatSessionService sessionService;
    private final ChatMessageService messageService;
    private final AiClientService aiClientService;
    private final AiProgressProducer progressProducer;
    private final AiTokenProducer tokenProducer;
    private final EmbeddingService embeddingService;
    private final RegenerationService regenerationService;
    private final PromptFactory promptFactory;
    private final BuildValidator buildValidator;
    private final BuildAutoFixer buildAutoFixer;
    private final AiResponseProducer producer;
    private final AiPartialProducer aiPartialProducer;
    private final UICriticService uiCriticService;
    private final MultiCandidateGenerationService multiCandidateGenerationService;
    private final CandidateJudgeService candidateJudgeService;
    private final PackageJsonEnricherService packageJsonEnricherService;
    private final PackageJsonSupport packageJsonSupport;
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
                files = handleInitialGeneration(event, sessionId);
                framework = resolveFrameworkFromFilesOrPrompt(files, event);
            } else {
                framework = resolveFramework(event);
                files = handleRegeneration(event, sessionId, framework);
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
            producer.sendResponse(event, session, files, framework);

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

    private List<GeneratedFile> handleInitialGeneration(AiRequestEvent event, String sessionId) {
        sendGlobalStatus(event.getProjectId(), sessionId,
                "🧠 Analyzing requirements...", "ANALYZING");
        sendProgress(event.getProjectId(), sessionId, null,
                "Planning project structure...", "PLANNING");
        sendGlobalStatus(event.getProjectId(), sessionId,
                "📐 Planning architecture, dependencies, and file order...", "PLANNING");

        ProjectSpec spec = aiClientService.planProject(event.getPrompt());
        String framework = spec.getFramework();

        List<String> plannedFiles = sanitizePlannedFiles(spec.getFiles());
        plannedFiles = ensureCoreFiles(plannedFiles, framework);
        plannedFiles = promptFactory.sortFilesForGeneration(plannedFiles);

        if (plannedFiles.isEmpty()) {
            throw new IllegalStateException("No files returned by planner");
        }

        sendProgress(event.getProjectId(), sessionId, null,
                "Planned " + plannedFiles.size() + " files for " + framework, "PLANNING");
        sendGlobalStatus(event.getProjectId(), sessionId,
                "📦 Planned " + plannedFiles.size() + " files using " + framework, "PLANNED");

        List<GeneratedFile> files = generateInitialProjectInPhases(
                event.getProjectId(),
                sessionId,
                event.getPrompt(),
                plannedFiles,
                framework,
                event
        );

        files = validateAndFixBuild(files, framework, event.getPrompt());
        files = critiqueAndImproveUi(
                event.getProjectId(),
                sessionId,
                event.getPrompt(),
                framework,
                files
        );
        files = packageJsonEnricherService.enrich(files, framework);

        files = validateAndFixBuild(files, framework, event.getPrompt());

        return files;
    }

    private List<GeneratedFile> handleRegeneration(AiRequestEvent event, String sessionId, String framework) {
        sendGlobalStatus(event.getProjectId(), sessionId,
                "♻️ Regenerating requested files...", "REGENERATING");

        List<GeneratedFile> files = regenerationService.regenerate(
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

        files = critiqueAndImproveUi(
                event.getProjectId(),
                sessionId,
                event.getPrompt(),
                framework,
                files
        );

        files = validateAndFixBuild(files, framework, event.getPrompt());
        return files;
    }

    private List<GeneratedFile> generateInitialProjectInPhases(
            String projectId,
            String sessionId,
            String userPrompt,
            List<String> plannedFiles,
            String framework,
            AiRequestEvent event
    ) {
        List<String> orderedFiles = promptFactory.sortFilesForGeneration(plannedFiles);
        List<List<String>> phases = buildGenerationPhasesGrouped(orderedFiles);
        List<GeneratedFile> allFiles = Collections.synchronizedList(new ArrayList<>());
        String cssEntryPath = promptFactory.getCssEntryPath(framework);

        ExecutorService executor = Executors.newFixedThreadPool(GENERATION_THREADS);
        Semaphore semaphore = new Semaphore(MAX_CONCURRENT_LLM_CALLS);

        try {
            for (int phaseIndex = 0; phaseIndex < phases.size(); phaseIndex++) {

                final int currentPhase = phaseIndex; // ✅ FIX for lambda

                List<String> phase = phases.get(phaseIndex);
                sendPhaseStatus(projectId, sessionId, phaseIndex);

                List<CompletableFuture<FileGenerationResult>> futures = new ArrayList<>();

                for (String filePath : phase) {

                    final String currentFilePath = filePath; // ✅ safe for lambda

                    CompletableFuture<FileGenerationResult> future =
                            CompletableFuture.supplyAsync(() -> {

                                try {
                                    sendProgress(projectId, sessionId, currentFilePath,
                                            "Generating " + currentFilePath, "GENERATING");

                                    sendInstantStart(projectId, sessionId, currentFilePath);

                                    GeneratedFile file;

                                    if (isBoilerplateFile(currentFilePath, framework)) {

                                        file = generateBoilerplateFile(currentFilePath, framework, userPrompt);

                                    } else if (currentFilePath.equals(cssEntryPath)) {

                                        List<GeneratedFile> jsxFilesSnapshot = snapshotUiFiles(allFiles);
                                        file = aiClientService.generateCssFile(
                                                jsxFilesSnapshot,
                                                userPrompt,
                                                framework
                                        );

                                    } else {

                                        semaphore.acquire();

                                        try {
                                            List<GeneratedFile> snapshot = new ArrayList<>(allFiles);

                                            String context = buildGenerationContext(
                                                    framework,
                                                    orderedFiles,
                                                    snapshot
                                            );

                                            PromptContext ctx = PromptContext.builder()
                                                    .projectId(projectId)
                                                    .sessionId(sessionId)
                                                    .userPrompt(userPrompt)
                                                    .mode(GenerationMode.INITIAL)
                                                    .framework(framework)
                                                    .rawContext(context)
                                                    .existingFiles(snapshot)
                                                    .targetFiles(Set.copyOf(orderedFiles))
                                                    .impactedFiles(Set.copyOf(orderedFiles))
                                                    .build();

                                            // ✅ CLEAN LOGIC (NO BUGS)
                                            if (isHighValueFile(currentFilePath, currentPhase)) {

                                                List<GenerationCandidate> candidates =
                                                        multiCandidateGenerationService.generateCandidates(
                                                                ctx,
                                                                currentFilePath,
                                                                2
                                                        );

                                                GenerationCandidate winner =
                                                        candidateJudgeService.judgeAndSelect(ctx, candidates);

                                                file = winner.getFile();

                                                log.info("Selected best candidate for {} with score {}",
                                                        currentFilePath,
                                                        winner.getScore() != null
                                                                ? winner.getScore().getTotalScore()
                                                                : -1);

                                            } else {
                                                file = aiClientService.generateSingleFile(ctx, currentFilePath);
                                            }

                                        } finally {
                                            semaphore.release();
                                        }
                                    }

                                    if (file == null || file.getContent() == null || file.getContent().isBlank()) {
                                        throw new IllegalStateException("Generated empty file for " + currentFilePath);
                                    }

                                    return new FileGenerationResult(currentFilePath, file);

                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                    throw new RuntimeException("Interrupted while generating " + currentFilePath, e);

                                } catch (Exception e) {
                                    throw new RuntimeException("Failed: " + currentFilePath, e);
                                }

                            }, executor);

                    futures.add(future);
                }

                // ✅ Collect results sequentially (streaming order maintained)
                for (CompletableFuture<FileGenerationResult> future : futures) {

                    FileGenerationResult result = future.join();
                    GeneratedFile file = result.file();

                    streamFileContent(projectId, sessionId, file);

                    allFiles.add(file);

                    aiPartialProducer.send(
                            AiPartialEvent.builder()
                                    .projectId(projectId)
                                    .sessionId(sessionId)
                                    .filePath(file.getPath())
                                    .content(file.getContent())
                                    .snapshotId(event.getSnapshotId())
                                    .snapshotTime(event.getSnapshotTime())
                                    .build()
                    );

                    sendProgress(projectId, sessionId, result.filePath(),
                            "Finished " + result.filePath(), "COMPLETED");
                }
            }
            CompletableFuture.runAsync(() -> {
                for (GeneratedFile file : allFiles) {
                    try {
                        embeddingService.storeFileEmbeddings(projectId, file);
                    } catch (Exception e) {
                        log.warn("Embedding failed for {}: {}", file.getPath(), e.getMessage());
                    }
                }
            });

        } finally {
            executor.shutdown();
        }

        return allFiles;
    }

    public List<GeneratedFile> validateAndFixBuild(
            List<GeneratedFile> files,
            String framework,
            String userPrompt
    ) {
        for (int attempt = 1; attempt <= VALIDATION_MAX_ATTEMPTS; attempt++) {
            log.info("🔍 Validation attempt {}", attempt);

            files = buildValidator.repairAll(files, framework);
            List<String> issues = buildValidator.validate(files, framework);

            if (issues.isEmpty()) {
                log.info("✅ Build validation passed");
                return files;
            }

            log.warn("⚠️ Found {} issues", issues.size());

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

    private List<GeneratedFile> critiqueAndImproveUi(
            String projectId,
            String sessionId,
            String userPrompt,
            String framework,
            List<GeneratedFile> files
    ) {
        PromptContext context = PromptContext.builder()
                .projectId(projectId)
                .sessionId(sessionId)
                .userPrompt(userPrompt)
                .framework(framework)
                .mode(GenerationMode.REGENERATE)
                .existingFiles(files)
                .build();

        sendGlobalStatus(projectId, sessionId,
                "🪄 Reviewing UI quality...", "CRITIQUING");

        UICriticReport critique = uiCriticService.critique(context, files);

        if (!critique.isRepairRecommended() || critique.getOverallScore() >= 85) {
            sendGlobalStatus(projectId, sessionId,
                    "✅ UI quality looks good", "CRITIQUED");
            return files;
        }

        sendGlobalStatus(projectId, sessionId,
                "🎨 Improving UI polish...", "IMPROVING_UI");

        List<GeneratedFile> updated = new ArrayList<>(files);

        if (critique.getSuggestions() == null) {
            return updated;
        }

        for (UIFixSuggestion suggestion : critique.getSuggestions()) {
            if (suggestion.getFilePath() == null || suggestion.getFilePath().isBlank()) continue;

            PromptContext fixContext = PromptContext.builder()
                    .projectId(projectId)
                    .sessionId(sessionId)
                    .userPrompt(userPrompt)
                    .framework(framework)
                    .mode(GenerationMode.REGENERATE)
                    .existingFiles(updated)
                    .build();

            try {
                String prompt = promptFactory.buildUiFixPrompt(fixContext, critique, suggestion.getFilePath());
                GeneratedFile improved = aiClientService.generateSingleFileWithPrompt(
                        prompt,
                        suggestion.getFilePath(),
                        framework
                );

                updated = replaceFile(updated, improved);

            } catch (Exception e) {
                log.warn("UI improvement failed for {}: {}", suggestion.getFilePath(), e.getMessage());
            }
        }

        return updated;
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

    private void sendPhaseStatus(String projectId, String sessionId, int phaseIndex) {
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
    }

    private void sendInstantStart(String projectId, String sessionId, String filePath) {
        List<String> fakeTokens = filePath.endsWith(".css")
                ? List.of(
                "/* Generating styles... */\n",
                "/* Building theme tokens... */\n"
        )
                : List.of(
                "import React from 'react';\n",
                "// Building component...\n"
        );

        CompletableFuture.runAsync(() -> {
            for (String token : fakeTokens) {
                tokenProducer.send(AiTokenEvent.builder()
                        .projectId(projectId)
                        .sessionId(sessionId)
                        .filePath(filePath)
                        .token(token)
                        .status("GENERATING")
                        .completed(false)
                        .build());

                sleepQuietly(10);
            }
        });
    }

    private CompletableFuture<Void> streamFileContent(String projectId, String sessionId, GeneratedFile file) {
        return CompletableFuture.runAsync(() -> {
            String content = file.getContent();
            if (content == null || content.isBlank()) {
                return;
            }

            List<String> chunks = splitIntoChunks(content, STREAM_CHUNK_SIZE);

            for (String chunk : chunks) {
                tokenProducer.send(AiTokenEvent.builder()
                        .projectId(projectId)
                        .sessionId(sessionId)
                        .filePath(file.getPath())
                        .status("GENERATING")
                        .token(chunk)
                        .completed(false)
                        .build());

                sleepQuietly(STREAM_DELAY_MS);
            }

            tokenProducer.send(AiTokenEvent.builder()
                    .projectId(projectId)
                    .sessionId(sessionId)
                    .filePath(file.getPath())
                    .status("GENERATING")
                    .token("")
                    .completed(true)
                    .build());
        });
    }


    private List<List<String>> buildGenerationPhasesGrouped(List<String> files) {

        List<String> phase1 = new ArrayList<>(); // infra
        List<String> phase2 = new ArrayList<>(); // app shell
        List<String> phase3 = new ArrayList<>(); // components
        List<String> phase4 = new ArrayList<>(); // pages
        List<String> phase5 = new ArrayList<>(); // css

        for (String file : files) {

            String normalized = file.replace("\\", "/").toLowerCase(Locale.ROOT).trim();
            String base = normalized.substring(normalized.lastIndexOf("/") + 1);

            // ❌ REMOVE legacy tailwind configs
            if (base.equals("tailwind.config.js") || base.equals("postcss.config.js")) {
                continue;
            }

            // 🔥 PHASE 1 — CORE INFRA
            if (base.equals("package.json")
                    || base.equals("vite.config.js")
                    || base.equals("vite.config.ts")
                    || base.equals("index.html")
                    || base.equals("next.config.js")
                    || base.equals("next.config.mjs")
                    || base.equals("angular.json")
                    || base.equals("tsconfig.json")) {

                phase1.add(file);
            }

            // 🔥 PHASE 2 — APP SHELL
            else if (base.startsWith("main.")
                    || normalized.endsWith("/layout.js")
                    || normalized.endsWith("/layout.jsx")
                    || normalized.endsWith("/layout.tsx")
                    || normalized.endsWith("/app.jsx")
                    || normalized.endsWith("/app.tsx")
                    || normalized.endsWith("/app.vue")) {

                phase2.add(file);
            }

            // 🔥 PHASE 3 — COMPONENT SYSTEM (NEW)
            else if (normalized.contains("/components/")) {
                phase3.add(file);
            }

            // 🔥 PHASE 4 — PAGES
            else if (normalized.endsWith("/page.js")
                    || normalized.endsWith("/page.jsx")
                    || normalized.endsWith("/page.tsx")) {

                phase4.add(file);
            }

            // 🔥 PHASE 5 — CSS (LAST ALWAYS)
            else if (base.endsWith(".css") || base.endsWith(".scss")) {
                phase5.add(file);
            }

            // 🔥 FALLBACK → treat as UI/page
            else {
                phase4.add(file);
            }
        }

        // 🔥 PRIORITY SORTING

        phase1.sort(Comparator.comparingInt(this::phase1Priority));
        phase2.sort(Comparator.comparingInt(this::phase2Priority));

        // components → stable order
        Collections.sort(phase3);

        List<List<String>> phases = new ArrayList<>();

        if (!phase1.isEmpty()) phases.add(phase1);
        if (!phase2.isEmpty()) phases.add(phase2);
        if (!phase3.isEmpty()) phases.add(phase3);
        if (!phase4.isEmpty()) phases.add(phase4);
        if (!phase5.isEmpty()) phases.add(phase5);

        return phases;
    }
    private int phase1Priority(String file) {
        String n = file.replace("\\", "/").toLowerCase(Locale.ROOT);
        if (n.endsWith("package.json")) return 0;
        if (n.contains("vite.config")) return 1;
        if (n.endsWith("index.html")) return 2;
        return 10;
    }

    private int phase2Priority(String file) {
        String n = file.replace("\\", "/").toLowerCase(Locale.ROOT);
        if (n.contains("/main.")) return 0;
        if (n.endsWith("/app.jsx") || n.endsWith("/app.tsx") || n.endsWith("/app.vue")) return 1;
        if (n.contains("/layout.")) return 2;
        return 10;
    }

    private List<List<String>> buildGenerationPhases(List<String> orderedFiles) {
        return buildGenerationPhasesGrouped(orderedFiles);
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

        int fileLimit = Math.min(generatedFiles.size(), 4);
        for (int i = 0; i < fileLimit; i++) {
            GeneratedFile f = generatedFiles.get(i);
            sb.append("FILE: ").append(f.getPath()).append("\n");

            String[] lines = f.getContent().split("\n");
            int lineLimit = Math.min(lines.length, 6);
            for (int j = 0; j < lineLimit; j++) {
                sb.append(lines[j]).append("\n");
            }
            if (lines.length > lineLimit) {
                sb.append("// ... truncated\n");
            }
            sb.append("-----\n");
        }

        return sb.toString();
    }

    private List<GeneratedFile> snapshotUiFiles(List<GeneratedFile> files) {
        return files.stream()
                .filter(f -> f.getPath().endsWith(".jsx")
                        || f.getPath().endsWith(".tsx")
                        || f.getPath().endsWith(".vue"))
                .limit(8)
                .collect(Collectors.toList());
    }

    private boolean isBoilerplateFile(String filePath, String framework) {
        String n = filePath.replace("\\", "/").toLowerCase(Locale.ROOT);
        String cssPath = promptFactory.getCssEntryPath(framework).replace("\\", "/").toLowerCase(Locale.ROOT);

        return n.endsWith("package.json")
                || n.endsWith("vite.config.js")
                || n.endsWith("vite.config.ts")
                || n.endsWith("index.html")
                || n.endsWith("tailwind.config.js")
                || n.endsWith("postcss.config.js")
                || n.equals(cssPath);
    }

    private GeneratedFile generateBoilerplateFile(String filePath, String framework, String userPrompt) {

        // ✅ CSS handled separately
        if (filePath.endsWith(".css")) {
            return aiClientService.generateCssFile(List.of(), userPrompt, framework);
        }

        // 🔥 CRITICAL FILES — NO AI

        switch (filePath) {

            case "package.json":
                return packageJsonSupport.fixPackageJson(List.of(), framework);

            case "vite.config.js":
                return GeneratedFile.builder()
                        .path("vite.config.js")
                        .content("""
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

export default defineConfig({
  plugins: [react(), tailwindcss()],
})
""")
                        .build();

            case "index.html":
                return GeneratedFile.builder()
                        .path("index.html")
                        .content("""
<!DOCTYPE html>
<html lang="en">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <title>AI App</title>
  </head>
  <body>
    <div id="root"></div>
    <script type="module" src="/src/main.jsx"></script>
  </body>
</html>
""")
                        .build();

            case "src/main.jsx":
                return GeneratedFile.builder()
                        .path("src/main.jsx")
                        .content("""
import React from 'react'
import ReactDOM from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import App from './App'
import './index.css'

ReactDOM.createRoot(document.getElementById('root')).render(
  <BrowserRouter>
    <App />
  </BrowserRouter>
)
""")
                        .build();

            case "src/App.jsx":
                return GeneratedFile.builder()
                        .path("src/App.jsx")
                        .content("""
import { Routes, Route } from 'react-router-dom'

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<div className="p-6">Home</div>} />
    </Routes>
  )
}
""")
                        .build();

            default:
                // ✅ Only non-critical files use AI
                return aiClientService.generateSingleFileWithPrompt(
                        buildBoilerplatePrompt(framework, filePath),
                        filePath,
                        framework
                );
        }
    }
    private String buildBoilerplatePrompt(String framework, String filePath) {
        return """
        Generate a strict boilerplate file.

        FRAMEWORK: %s
        FILE: %s

        RULES:
        - deterministic
        - minimal but valid
        - production-safe
        - no explanations
        - return ONLY valid JSON:
          { "path": "...", "content": "..." }

        DO NOT:
        - include markdown
        - include comments outside code
        """.formatted(framework, filePath);
    }
    private List<GeneratedFile> replaceFile(List<GeneratedFile> files, GeneratedFile updated) {
        boolean exists = files.stream().anyMatch(f -> f.getPath().equals(updated.getPath()));

        List<GeneratedFile> result = files.stream()
                .map(f -> f.getPath().equals(updated.getPath()) ? updated : f)
                .collect(Collectors.toCollection(ArrayList::new));

        if (!exists) {
            result.add(updated);
        }
        return result;
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

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private String resolveFramework(AiRequestEvent event) {
        if (event.getFramework() != null && !event.getFramework().isBlank()) {
            return event.getFramework();
        }
        return promptFactory.detectFramework(event.getPrompt());
    }

    private String resolveFrameworkFromFilesOrPrompt(List<GeneratedFile> files, AiRequestEvent event) {
        return resolveFramework(event);
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

    private List<String> ensureCoreFiles(List<String> plannedFiles, String framework) {

        LinkedHashSet<String> files = new LinkedHashSet<>(plannedFiles);

        // 🔥 ALWAYS REQUIRED
        files.add("package.json");

        if ("react-vite".equals(framework)) {
            files.add("index.html");
            files.add("vite.config.js");
            files.add("src/main.jsx");
            files.add("src/App.jsx");
            files.add("src/index.css");
        }

        if ("next".equals(framework)) {
            files.add("app/layout.jsx");
            files.add("app/page.jsx");
            files.add("app/globals.css");
        }

        // Tailwind v3 only
        if (!framework.equals("vue-vite")) {
            files.add("tailwind.config.js");
            files.add("postcss.config.js");
        }

        return new ArrayList<>(files);
    }
    private void ensurePresent(List<String> files, String path) {
        boolean exists = files.stream().anyMatch(f -> f.equalsIgnoreCase(path));
        if (!exists) {
            files.add(path);
        }
    }

    private boolean shouldUseCandidates(String filePath) {
        String lower = filePath.toLowerCase(Locale.ROOT);

        return lower.contains("landing")
                || lower.contains("home")
                || lower.contains("dashboard")
                || lower.endsWith("/page.jsx")
                || lower.endsWith("/page.tsx");
    }

    private String safeErrorMessage(Exception e) {
        String msg = e.getMessage();
        return (msg == null || msg.isBlank()) ? "Unknown error" : msg;
    }

    private record FileGenerationResult(String filePath, GeneratedFile file) {}
    private boolean isHighValueFile(String filePath, int phaseIndex) {
        String f = filePath.toLowerCase();

        // ❌ NEVER use candidates in early phases
        if (phaseIndex < 2) return false;

        // ✅ Only UI-critical files
        return f.contains("landing")
                || f.contains("home")
                || f.contains("dashboard")
                || f.endsWith("/page.jsx")
                || f.endsWith("/page.tsx");
    }
}