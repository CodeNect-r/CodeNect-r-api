package com.lovable.ai_service.producer;

import com.lovable.ai_service.dto.AiTokenEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AiTokenProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void send(AiTokenEvent event) {
        kafkaTemplate.send("ai.token", event.getProjectId(), event);
    }
}