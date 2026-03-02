package com.lovable.project_service.producer;

import com.lovable.project_service.dto.AiRequestEvent;

import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AiRequestProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    public void send(AiRequestEvent event) {
        kafkaTemplate.send("ai.request", event).whenComplete((result, ex) -> {
            if (ex == null) {
                System.out.println("✅ PRODUCER SUCCESS: Sent to topic " + result.getRecordMetadata().topic());
            } else {
                System.err.println("❌ PRODUCER ERROR: " + ex.getMessage());
            }
        });    }
}