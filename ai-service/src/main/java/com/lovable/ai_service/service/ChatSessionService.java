package com.lovable.ai_service.service;

import com.lovable.ai_service.dto.AiRequestEvent;
import com.lovable.ai_service.dto.ChatSessionResponse;
import com.lovable.ai_service.entity.ChatSession;
import com.lovable.ai_service.repository.ChatSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChatSessionService {

    private final ChatSessionRepository repository;

    public ChatSession create(String projectId, String userEmail, String title) {
        ChatSession session = ChatSession.builder()
                .projectId(projectId)
                .userEmail(userEmail)
                .title(title == null || title.isBlank() ? "New Chat" : title.trim())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        return repository.save(session);
    }

    public List<ChatSessionResponse> list(String projectId, String userEmail) {
        return repository.findByProjectIdAndUserEmailOrderByUpdatedAtDesc(projectId, userEmail)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public ChatSession getOwned(String sessionId, String projectId, String userEmail) {
        ChatSession session = repository.findById(UUID.fromString(sessionId))
                .orElseThrow(() -> new RuntimeException("Session not found"));

        if (!session.getProjectId().equals(projectId) || !session.getUserEmail().equals(userEmail)) {
            throw new RuntimeException("Session does not belong to this user/project");
        }
        return session;
    }

    public ChatSession getOrCreate(AiRequestEvent event) {
        if (event.getSessionId() == null || event.getSessionId().isBlank()) {
            return create(event.getProjectId(), event.getUserEmail(), deriveTitle(event.getPrompt()));
        }
        return getOwned(event.getSessionId(), event.getProjectId(), event.getUserEmail());
    }

    private ChatSessionResponse toResponse(ChatSession session) {
        return ChatSessionResponse.builder()
                .id(session.getId().toString())
                .projectId(session.getProjectId())
                .title(session.getTitle())
                .createdAt(session.getCreatedAt())
                .updatedAt(session.getUpdatedAt())
                .build();
    }

    private String deriveTitle(String prompt) {
        if (prompt == null || prompt.isBlank()) return "New Chat";
        String normalized = prompt.trim().replaceAll("\\s+", " ");
        return normalized.length() <= 50 ? normalized : normalized.substring(0, 50) + "...";
    }
}