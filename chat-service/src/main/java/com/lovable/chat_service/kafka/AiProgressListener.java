package com.lovable.chat_service.kafka;

import com.lovable.chat_service.dto.AiProgressEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AiProgressListener {

    private final SimpMessagingTemplate messagingTemplate;

    @KafkaListener(topics = "ai.progress", groupId = "chat-service")
    public void onProgress(AiProgressEvent event) {
        if (event == null || event.getProjectId() == null) {
            System.err.println("Progress event missing projectId: " + event);
            return;
        }

                // Always forward initial/progress updates on a project-scoped topic
        messagingTemplate.convertAndSend(
                "/topic/project/" + event.getProjectId() + "/generation",
                event
        );
    }
}