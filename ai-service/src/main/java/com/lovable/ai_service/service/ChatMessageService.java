package com.lovable.ai_service.service;

import com.lovable.ai_service.entity.*;
import com.lovable.ai_service.repository.ChatMessageRepository;
import com.lovable.ai_service.repository.ChatSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChatMessageService {

    private final ChatMessageRepository messageRepository;
    private final ChatSessionRepository sessionRepository;

    public void saveUserMessage(UUID sessionId, String content) {
        save(sessionId, MessageRole.USER, content);
    }

    public void saveAiMessage(UUID sessionId, String content) {
        save(sessionId, MessageRole.AI, content);
    }

    private void save(UUID sessionId, MessageRole role, String content) {

        ChatSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("Session not found"));

        ChatMessage message = ChatMessage.builder()
                .session(session)
                .role(role.name())
                .content(content)
                .createdAt(LocalDateTime.now())
                .build();

        messageRepository.save(message);

        // Update session activity timestamp
        session.setUpdatedAt(LocalDateTime.now());
        sessionRepository.save(session);
    }
}