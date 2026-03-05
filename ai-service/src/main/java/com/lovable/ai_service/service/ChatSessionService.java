package com.lovable.ai_service.service;

import com.lovable.ai_service.dto.AiRequestEvent;
import com.lovable.ai_service.entity.ChatSession;
import com.lovable.ai_service.repository.ChatSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChatSessionService {

    private final ChatSessionRepository repository;

    public ChatSession getOrCreate(AiRequestEvent event) {

        if (event.getSessionId() == null) {
            return repository.save(
                    ChatSession.builder()
                            .projectId(event.getProjectId())
                            .userEmail(event.getUserEmail())
                            .createdAt(LocalDateTime.now())
                            .updatedAt(LocalDateTime.now())
                            .build()
            );
        }

        ChatSession session = repository.findById(UUID.fromString(event.getSessionId()))
                .orElseThrow(() ->
                        new RuntimeException("Session not found: " + event.getSessionId())
                );

        // 🔐 Security validation
        if (!session.getProjectId().equals(event.getProjectId()) ||
                !session.getUserEmail().equals(event.getUserEmail())) {

            throw new RuntimeException("Session does not belong to this user/project");
        }

        return session;
    }
}