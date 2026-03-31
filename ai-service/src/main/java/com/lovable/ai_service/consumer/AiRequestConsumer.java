package com.lovable.ai_service.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lovable.ai_service.dto.AiRequestEvent;
import com.lovable.ai_service.service.AiOrchestratorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
@RequiredArgsConstructor
@Slf4j
public class AiRequestConsumer {

    private final AiOrchestratorService orchestrator;

    // 🔥 Thread pool (prevents Kafka blocking)
    private final ExecutorService executor = Executors.newFixedThreadPool(5);

    @KafkaListener(topics = "ai.request", groupId = "ai-service-group-v4")
    public void consume(String message) {

        executor.submit(() -> {
            try {
                AiRequestEvent event =
                        new ObjectMapper().readValue(message, AiRequestEvent.class);

                orchestrator.process(event);

            } catch (Exception e) {
                log.error("AI processing failed", e);
            }
        });
    }
}