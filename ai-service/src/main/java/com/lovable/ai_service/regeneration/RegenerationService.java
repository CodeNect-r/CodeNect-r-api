package com.lovable.ai_service.regeneration;

import com.lovable.ai_service.dto.AiProgressEvent;
import com.lovable.ai_service.dto.GeneratedFile;
import com.lovable.ai_service.dto.GenerationMode;
import com.lovable.ai_service.producer.AiProgressProducer;
import com.lovable.ai_service.service.AiClientService;
import com.lovable.ai_service.service.EmbeddingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;

@Service
@RequiredArgsConstructor
public class RegenerationService {

    private final ImpactAnalyzer impactAnalyzer;
    private final AiClientService aiClientService;
    private final EmbeddingService embeddingService;
    private final AiProgressProducer progressProducer;

    private final ExecutorService pool = Executors.newFixedThreadPool(3);

    public List<GeneratedFile> regenerate(
            String projectId,
            String userPrompt,
            String sessionId,
            String framework
    ) {
        Set<String> impactedFiles = impactAnalyzer.detectImpactedFiles(projectId, userPrompt);

        if (impactedFiles.isEmpty()) {
            progressProducer.send(AiProgressEvent.builder()
                    .projectId(projectId)
                    .sessionId(sessionId)
                    .message("No strongly impacted files detected. Skipping regeneration.")
                    .status("DONE")
                    .build());
            return List.of();
        }

        String context = embeddingService.getProjectContext(projectId, impactedFiles);

        progressProducer.send(AiProgressEvent.builder()
                .projectId(projectId)
                .sessionId(sessionId)
                .message("Detected " + impactedFiles.size() + " impacted files")
                .status("PLANNING")
                .build());

        List<CompletableFuture<GeneratedFile>> futures = new ArrayList<>();

        for (String filePath : impactedFiles) {
            futures.add(CompletableFuture.supplyAsync(() -> {
                progressProducer.send(AiProgressEvent.builder()
                        .projectId(projectId)
                        .sessionId(sessionId)
                        .filePath(filePath)
                        .message("Regenerating " + filePath)
                        .status("GENERATING")
                        .build());

                GeneratedFile updated = aiClientService.generateSingleFile(
                        context,
                        userPrompt,
                        filePath,
                        impactedFiles,
                        GenerationMode.REGENERATE,
                        framework


                );

                embeddingService.storeFileEmbeddings(projectId, updated);

                progressProducer.send(AiProgressEvent.builder()
                        .projectId(projectId)
                        .sessionId(sessionId)
                        .filePath(filePath)
                        .message("Finished " + filePath)
                        .status("COMPLETED")
                        .build());

                return updated;
            }, pool));
        }

        return futures.stream().map(CompletableFuture::join).toList();
    }
}