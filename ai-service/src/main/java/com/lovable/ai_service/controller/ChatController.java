package com.lovable.ai_service.controller;


import com.lovable.ai_service.entity.ChatMessage;
import com.lovable.ai_service.repository.ChatMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatMessageRepository repository;

    @GetMapping("/{sessionId}")
    public List<ChatMessage> getMessages(@PathVariable UUID sessionId) {
        return repository.findBySessionIdOrderByCreatedAtAsc(sessionId);
    }
}

