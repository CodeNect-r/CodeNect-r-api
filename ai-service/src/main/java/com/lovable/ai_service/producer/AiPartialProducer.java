package com.lovable.ai_service.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lovable.ai_service.dto.AiPartialEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AiPartialProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public void send(AiPartialEvent event) {
        try {
            String json = objectMapper.writeValueAsString(event);
            kafkaTemplate.send("ai.partial", event.getProjectId(), json);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize AiPartialEvent", e);
        }
    }
}