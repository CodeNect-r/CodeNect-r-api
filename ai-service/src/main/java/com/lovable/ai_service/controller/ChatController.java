package com.lovable.ai_service.controller;

import com.lovable.ai_service.dto.ChatSessionResponse;
import com.lovable.ai_service.dto.CreateChatSessionRequest;
import com.lovable.ai_service.entity.ChatMessage;
import com.lovable.ai_service.entity.ChatSession;
import com.lovable.ai_service.repository.ChatMessageRepository;
import com.lovable.ai_service.service.ChatSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatMessageRepository messageRepository;
    private final ChatSessionService sessionService;

    @GetMapping("/sessions")
    public List<ChatSessionResponse> listSessions(@RequestParam String projectId, Authentication auth) {
        return sessionService.list(projectId, auth.getName());
    }

    @PostMapping("/sessions")
    public ChatSessionResponse createSession(@RequestBody CreateChatSessionRequest request, Authentication auth) {
        ChatSession session = sessionService.create(request.getProjectId(), auth.getName(), request.getTitle());
        return ChatSessionResponse.builder()
                .id(session.getId().toString())
                .projectId(session.getProjectId())
                .title(session.getTitle())
                .createdAt(session.getCreatedAt())
                .updatedAt(session.getUpdatedAt())
                .build();
    }

    @GetMapping("/{sessionId}")
    public List<ChatMessage> getMessages(@PathVariable String sessionId,
                                         @RequestParam String projectId,
                                         Authentication auth) {
        sessionService.getOwned(sessionId, projectId, auth.getName());
        return messageRepository.findBySessionIdOrderByCreatedAtAsc(UUID.fromString(sessionId));
    }
}
