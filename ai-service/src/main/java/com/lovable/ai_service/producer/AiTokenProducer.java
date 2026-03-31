package com.lovable.ai_service.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lovable.ai_service.dto.AiTokenEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AiTokenProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public void send(AiTokenEvent event) {
        try {
            kafkaTemplate.send("ai.token",
                    event.getProjectId(),
                    objectMapper.writeValueAsString(event));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}