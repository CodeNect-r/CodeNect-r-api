package com.lovable.ai_service.producer;

import com.lovable.ai_service.dto.AiProgressEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AiProgressProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void send(AiProgressEvent event) {
        kafkaTemplate.send("ai.progress", event.getProjectId(), event);
    }
}