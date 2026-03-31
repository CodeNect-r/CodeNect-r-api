package com.lovable.chat_service.controller;

import com.lovable.chat_service.dto.*;
import com.lovable.chat_service.kafka.AiRequestProducer;
import com.lovable.chat_service.kafka.ProjectCreateProducer;
import com.lovable.chat_service.service.ProjectCreateReplyHandler;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Controller
@RequiredArgsConstructor
public class ChatWebSocketController {

    private final AiRequestProducer aiRequestProducer;
    private final ProjectCreateProducer projectCreateProducer;
    private final ProjectCreateReplyHandler replyHandler;
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/chat.send")
    public void handle(@Valid @Payload ChatRequest request, Principal principal) throws Exception {

        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            throw new IllegalStateException("Unauthenticated websocket user");
        }

        String userEmail = principal.getName();
        String projectId = request.getProjectId();

        System.out.println("PROMPT RECEIVED: " + request.getPrompt());

        // FIRST PROMPT → create project only
//
        // EXISTING PROJECT → modify through AI

        AiRequestEvent aiRequest = AiRequestEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventVersion("v1")
                .projectId(projectId)
                .sessionId(request.getSessionId()) // use existing session
                .userEmail(userEmail)
                .prompt(request.getPrompt())
                .operationType(OperationType.MODIFY_PROJECT)
                .build();

        aiRequestProducer.send(aiRequest);
    }

    private String generateProjectName(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return "Untitled Project";
        }

        String normalized = prompt.trim().replaceAll("\\s+", " ");
        return normalized.length() <= 40 ? normalized : normalized.substring(0, 40);
    }

    @MessageExceptionHandler
    @SendToUser("/queue/errors")
    public String handleException(Exception ex) {
        return ex.getMessage();
    }
}