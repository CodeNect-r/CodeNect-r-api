package com.lovable.ai_service.service;

import com.lovable.ai_service.dto.*;
import com.lovable.ai_service.entity.ChatSession;
import com.lovable.ai_service.producer.AiProgressProducer;
import com.lovable.ai_service.producer.AiResponseProducer;
import com.lovable.ai_service.regeneration.RegenerationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AiOrchestratorService {

    private final ChatSessionService sessionService;
    private final ChatMessageService messageService;
    private final AiClientService aiClientService;
    private final AiResponseProducer responseProducer;
    private final AiProgressProducer progressProducer;
    private final EmbeddingService embeddingService;
    private final RegenerationService regenerationService;

    @Transactional
    public void process(AiRequestEvent event) {

        ChatSession session = sessionService.getOrCreate(event);
        System.out.println("session:"+session);

        messageService.saveUserMessage(
                session.getId(),
                event.getPrompt()
        );
        System.out.println("operationtype:"+ event.getOperationType());
        GenerationMode mode =
                "INITIAL_PROJECT".equals(event.getOperationType())
                        ? GenerationMode.INITIAL
                        : GenerationMode.REGENERATE;

        List<GeneratedFile> files;

        if (mode == GenerationMode.INITIAL) {

            progressProducer.send(
                    AiProgressEvent.builder()
                            .projectId(event.getProjectId())
                            .sessionId(session.getId().toString())
                            .message("Planning project structure...")
                            .status("PLANNING")
                            .build()
            );

            List<GeneratedFile> generated =
                    aiClientService.generateFiles(
                            "",
                            event.getPrompt(),
                            event.getProjectId(),
                            null,
                            GenerationMode.INITIAL
                    );

            files = new ArrayList<>();

            for (GeneratedFile file : generated) {

                progressProducer.send(
                        AiProgressEvent.builder()
                                .projectId(event.getProjectId())
                                .sessionId(session.getId().toString())
                                .filePath(file.getPath())
                                .message("Generating " + file.getPath())
                                .status("GENERATING")
                                .build()
                );

                embeddingService.storeFileEmbeddings(
                        event.getProjectId(),
                        file
                );

                files.add(file);
            }

        } else {

            files = regenerationService.regenerate(
                    event.getProjectId(),
                    event.getPrompt()
            );
        }

        progressProducer.send(
                AiProgressEvent.builder()
                        .projectId(event.getProjectId())
                        .sessionId(session.getId().toString())
                        .status("DONE")
                        .build()
        );

        messageService.saveAiMessage(
                session.getId(),
                "Generated " + files.size() + " files"
        );

        responseProducer.sendResponse(event, session, files);
    }
}