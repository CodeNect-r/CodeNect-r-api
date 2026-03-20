package com.lovable.chat_service.kafka;

import com.lovable.chat_service.dto.PreviewReadyEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PreviewReadyListener {

    private final SimpMessagingTemplate messagingTemplate;

    @KafkaListener(topics = "preview.ready", groupId = "chat-service-group")
    public void handlePreviewReady(PreviewReadyEvent event) {

        System.out.println("🚀 Preview Ready Event Received: " + event);

        messagingTemplate.convertAndSend(
                "/topic/project/" + event.getProjectId() + "/preview",
                event
        );
    }
}