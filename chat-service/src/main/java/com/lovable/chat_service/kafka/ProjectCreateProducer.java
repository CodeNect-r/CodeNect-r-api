package com.lovable.chat_service.kafka;

import com.lovable.chat_service.dto.CreateProjectEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProjectCreateProducer {
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void send(CreateProjectEvent event) {
        System.out.println("request sent");
        kafkaTemplate.send(
                "project.create.request",
                event.getRequestId(),
                event
        );

    }
}
