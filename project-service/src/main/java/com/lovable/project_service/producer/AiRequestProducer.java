package com.lovable.project_service.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lovable.project_service.dto.AiRequestEvent;

import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AiRequestProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public void send(AiRequestEvent event) {
        try {
            kafkaTemplate.send(
                    "ai.request",
                    event.getProjectId(),
                    objectMapper.writeValueAsString(event)
            ).whenComplete((result, ex) -> {
                if (ex == null) {
                    System.out.println("✅ SENT TO KAFKA: topic="
                            + result.getRecordMetadata().topic()
                            + " partition=" + result.getRecordMetadata().partition()
                            + " offset=" + result.getRecordMetadata().offset());
                } else {
                    System.out.println("❌ KAFKA SEND FAILED: " + ex.getMessage());
                }});
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}