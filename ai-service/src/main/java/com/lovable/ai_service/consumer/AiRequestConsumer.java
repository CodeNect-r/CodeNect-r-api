package com.lovable.ai_service.consumer;


import com.lovable.ai_service.dto.AiRequestEvent;
import com.lovable.ai_service.service.AiOrchestratorService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AiRequestConsumer {

    private final AiOrchestratorService service;


    @KafkaListener(
            topics = "ai.request",
            groupId = "ai-service-group-v4"
    )
    public void listen(AiRequestEvent event) {

        System.out.println("Message Received: " + event.getPrompt());

        try {
            service.process(event);
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }
    }
}