package com.lovable.project_service.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lovable.project_service.dto.AiPartialEvent;
import com.lovable.project_service.entity.Project;
import com.lovable.project_service.repository.ProjectRepository;
import com.lovable.project_service.service.FileVersioningService;
import lombok.*;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AiPartialListener {

    private final FileVersioningService fileVersioningService;
    private final ProjectRepository projectRepository;

    @KafkaListener(topics = "ai.partial", groupId = "project-service-group-v2")
    public void handle(String message) throws Exception {

        AiPartialEvent event =
                new ObjectMapper().readValue(message, AiPartialEvent.class);

        Project project = projectRepository.findById(event.getProjectId()).orElseThrow();

        if (!event.getSnapshotId().equals(project.getLatestSnapshotId())) {
            return;
        }

        fileVersioningService.saveOrUpdateFile(
                event.getProjectId(),
                event.getFilePath(),
                event.getContent()
        );
    }
}