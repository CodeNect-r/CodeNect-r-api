package com.lovable.project_service.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lovable.project_service.dto.AiResponseEvent;
import com.lovable.project_service.repository.ProjectRepository;
import com.lovable.project_service.service.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AiResponseConsumer {

    private final ProjectService projectService;

    private final ProjectRepository projectRepository;
    @KafkaListener(topics = "ai.response", groupId = "project-service-group")
    public void consume(String message) throws Exception {

        AiResponseEvent event =
                new ObjectMapper().readValue(message, AiResponseEvent.class);

        projectService.handleAiResponse(event);
    }
}