package com.lovable.ai_service.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lovable.ai_service.dto.AiProgressEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AiProgressProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public void send(AiProgressEvent event) {
        try {
            kafkaTemplate.send("ai.progress",
                    event.getProjectId(),
                    objectMapper.writeValueAsString(event));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}