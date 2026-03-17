package com.lovable.chat_service.kafka;

import com.lovable.chat_service.dto.AiRequestEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AiRequestProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void send(AiRequestEvent event) {
        kafkaTemplate.send("ai.request", event.getProjectId(),event);
    }
}