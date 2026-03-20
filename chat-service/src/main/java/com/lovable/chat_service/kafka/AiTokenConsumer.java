package com.lovable.chat_service.kafka;

import com.lovable.chat_service.dto.AiTokenEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AiTokenConsumer {

    private final SimpMessagingTemplate messagingTemplate;

    @KafkaListener(topics = "ai.token", groupId = "chat-service")
    public void consume(AiTokenEvent event) {
        if (event == null || event.getProjectId() == null || event.getSessionId() == null) {
            return;
        }

        messagingTemplate.convertAndSend(
                "/topic/project/" + event.getProjectId() + "/generation",event
        );
    }
}