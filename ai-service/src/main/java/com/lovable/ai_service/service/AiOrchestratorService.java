package com.lovable.ai_service.service;

import com.lovable.ai_service.dto.*;
import com.lovable.ai_service.entity.ChatSession;
import com.lovable.ai_service.producer.AiProgressProducer;
import com.lovable.ai_service.producer.AiResponseProducer;
import com.lovable.ai_service.producer.AiTokenProducer;
import com.lovable.ai_service.regeneration.RegenerationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.*;

@Service
@RequiredArgsConstructor
public class AiOrchestratorService {

    private final ChatSessionService sessionService;
    private final ChatMessageService messageService;
    private final AiClientService aiClientService;
    private final AiResponseProducer responseProducer;
    private final AiProgressProducer progressProducer;
    private final AiTokenProducer tokenProducer;
    private final EmbeddingService embeddingService;
    private final RegenerationService regenerationService;

    private final ExecutorService pool = Executors.newFixedThreadPool(3);

    @Transactional
    public void process(AiRequestEvent event) {
        ChatSession session = sessionService.getOrCreate(event);

        messageService.saveUserMessage(session.getId(), event.getPrompt());

        GenerationMode mode =
                "INITIAL_PROJECT".equals(event.getOperationType())
                        ? GenerationMode.INITIAL
                        : GenerationMode.REGENERATE;

        List<GeneratedFile> files;

        String framework = "unknown";

        if (mode == GenerationMode.INITIAL) {
            progressProducer.send(AiProgressEvent.builder()
                    .projectId(event.getProjectId())
                    .sessionId(session.getId().toString())
                    .message("Planning project structure...")
                    .status("PLANNING")
                    .build());

            ProjectSpec spec = aiClientService.planProject(event.getPrompt());
            framework = spec.getFramework();

            List<String> plannedFiles = sanitizePlannedFiles(spec.getFiles());

            progressProducer.send(AiProgressEvent.builder()
                    .projectId(event.getProjectId())
                    .sessionId(session.getId().toString())
                    .message("Planned " + plannedFiles.size() + " files for " + framework)
                    .status("PLANNING")
                    .build());

            files = generateInitialProjectInPhases(
                    event.getProjectId(),
                    session.getId().toString(),
                    event.getPrompt(),
                    plannedFiles,
                    framework
            );

        } else {
            files = regenerationService.regenerate(
                    event.getProjectId(),
                    event.getPrompt(),
                    session.getId().toString(),
                    framework
            );
        }

        progressProducer.send(AiProgressEvent.builder()
                .projectId(event.getProjectId())
                .sessionId(session.getId().toString())
                .message("Syncing preview...")
                .status("DONE")
                .build());

        String summary = aiClientService.summarizeResult(
                event.getPrompt(),
                framework,
                files,
                mode
        );

        tokenProducer.send(AiTokenEvent.builder()
                .projectId(event.getProjectId())
                .sessionId(session.getId().toString())
                .token(summary)
                .completed(false)
                .build());

        tokenProducer.send(AiTokenEvent.builder()
                .projectId(event.getProjectId())
                .sessionId(session.getId().toString())
                .token("")
                .completed(true)
                .build());

        messageService.saveAiMessage(session.getId(), summary);

        responseProducer.sendResponse(event, session, files,framework);
    }

    private List<GeneratedFile> generateInitialProjectInPhases(
            String projectId,
            String sessionId,
            String userPrompt,
            List<String> plannedFiles,
            String framework
    ) {
        List<List<String>> phases = buildGenerationPhases(plannedFiles);
        List<GeneratedFile> allFiles = new ArrayList<>();

        for (List<String> phase : phases) {
            List<CompletableFuture<GeneratedFile>> futures = new ArrayList<>();

            for (String filePath : phase) {
                futures.add(CompletableFuture.supplyAsync(() -> {
                    progressProducer.send(AiProgressEvent.builder()
                            .projectId(projectId)
                            .sessionId(sessionId)
                            .filePath(filePath)
                            .message("Generating " + filePath)
                            .status("GENERATING")
                            .build());

                    String context = buildContextFromGenerated(allFiles);

                    GeneratedFile file = aiClientService.generateSingleFile(
                            context,
                            userPrompt,
                            filePath,
                            null,
                            GenerationMode.INITIAL,
                            framework
                    );

                    embeddingService.storeFileEmbeddings(projectId, file);

                    progressProducer.send(AiProgressEvent.builder()
                            .projectId(projectId)
                            .sessionId(sessionId)
                            .filePath(filePath)
                            .message("Finished " + filePath)
                            .status("COMPLETED")
                            .build());

                    return file;
                }, pool));
            }

            List<GeneratedFile> generatedThisPhase = futures.stream()
                    .map(CompletableFuture::join)
                    .toList();

            allFiles.addAll(generatedThisPhase);
        }

        return allFiles;
    }

    private List<List<String>> buildGenerationPhases(List<String> files) {
        List<String> phase1 = new ArrayList<>();
        List<String> phase2 = new ArrayList<>();
        List<String> phase3 = new ArrayList<>();

        for (String file : files) {
            String normalized = file.toLowerCase(Locale.ROOT);

            if (normalized.equals("package.json")
                    || normalized.equals("vite.config.js")
                    || normalized.equals("vite.config.ts")
                    || normalized.equals("index.html")
                    || normalized.equals("next.config.js")
                    || normalized.equals("angular.json")) {
                phase1.add(file);
            } else if (normalized.contains("main.")
                    || normalized.endsWith("/layout.js")
                    || normalized.endsWith("/page.js")
                    || normalized.contains("app.jsx")
                    || normalized.contains("app.vue")) {
                phase2.add(file);
            } else {
                phase3.add(file);
            }
        }

        List<List<String>> phases = new ArrayList<>();
        if (!phase1.isEmpty()) phases.add(phase1);
        if (!phase2.isEmpty()) phases.add(phase2);
        if (!phase3.isEmpty()) phases.add(phase3);
        return phases;
    }

    private List<String> sanitizePlannedFiles(List<String> files) {
        if (files == null) return List.of();

        LinkedHashSet<String> orderedUnique = new LinkedHashSet<>();
        for (String file : files) {
            if (file != null && !file.isBlank()) {
                orderedUnique.add(file.trim());
            }
        }
        return new ArrayList<>(orderedUnique);
    }

    private String buildContextFromGenerated(List<GeneratedFile> files) {
        if (files.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        for (GeneratedFile file : files) {
            sb.append("FILE: ").append(file.getPath()).append("\n")
                    .append(file.getContent()).append("\n")
                    .append("-----\n");
        }
        return sb.toString();
    }
}