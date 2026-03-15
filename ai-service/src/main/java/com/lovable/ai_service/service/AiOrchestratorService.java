package com.lovable.ai_service.service;

import com.lovable.ai_service.dto.AiRequestEvent;
import com.lovable.ai_service.dto.AiTokenEvent;
import com.lovable.ai_service.dto.GeneratedFile;
import com.lovable.ai_service.entity.ChatSession;
import com.lovable.ai_service.dto.GenerationMode;
import com.lovable.ai_service.producer.AiResponseProducer;
import com.lovable.ai_service.producer.AiTokenProducer;
import com.lovable.ai_service.regeneration.RegenerationService;
import com.lovable.ai_service.repository.DocumentEmbeddingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AiOrchestratorService {

    private final ChatSessionService sessionService;
    private final ChatMessageService messageService;
    private final AiClientService aiClientService;
    private final AiResponseProducer producer;
    private final EmbeddingService embeddingService;
    private final RegenerationService regenerationService;
    private final DocumentEmbeddingRepository embeddingRepository;
    private final AiTokenProducer tokenProducer;

    @Transactional
    public void process(AiRequestEvent event) {

        ChatSession session = sessionService.getOrCreate(event);

        messageService.saveUserMessage(
                session.getId(),
                event.getPrompt()
        );

        GenerationMode mode =
                "INITIAL_PROJECT".equals(event.getOperationType())
                        ? GenerationMode.INITIAL
                        : GenerationMode.REGENERATE;

        List<GeneratedFile> files;

        if (mode == GenerationMode.INITIAL) {

            files = aiClientService.generateFiles(
                    "",
                    event.getPrompt(),
                    event.getProjectId(),
                    null,
                    GenerationMode.INITIAL
            );

            for (GeneratedFile file : files) {
                embeddingService.storeFileEmbeddings(
                        event.getProjectId(),
                        file
                );
            }

        } else {

            files = regenerationService.regenerate(
                    event.getProjectId(),
                    event.getPrompt()
            );
        }

        // 🔥 Stream small summary (UI feedback)
        tokenProducer.send(
                AiTokenEvent.builder()
                        .projectId(event.getProjectId())
                        .sessionId(session.getId().toString())
                        .token("Generated " + files.size() + " files\n")
                        .completed(false)
                        .build()
        );

        tokenProducer.send(
                AiTokenEvent.builder()
                        .projectId(event.getProjectId())
                        .sessionId(session.getId().toString())
                        .token("")
                        .completed(true)
                        .build()
        );

        messageService.saveAiMessage(
                session.getId(),
                "Generated " + files.size() + " files"
        );

        producer.sendResponse(event, session, files);
    }
}