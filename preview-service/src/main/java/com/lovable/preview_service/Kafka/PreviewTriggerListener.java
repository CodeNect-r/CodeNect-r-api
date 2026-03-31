package com.lovable.preview_service.Kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lovable.preview_service.dto.PreviewTriggerEvent;
import com.lovable.preview_service.service.PreviewOrchestratorService;
import com.lovable.preview_service.service.PreviewStreamBufferService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PreviewTriggerListener {

    private final PreviewOrchestratorService orchestrator;
    private final PreviewStreamBufferService bufferService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "preview.trigger", groupId = "preview-service-group")
    public void onPreviewTrigger(String message) {
        try {
            PreviewTriggerEvent event =
                    objectMapper.readValue(message, PreviewTriggerEvent.class);

            bufferService.flushAllForProject(
                    event.getProjectId(),
                    event.getSnapshotId()
            );

            orchestrator.startPreview(
                    event.getProjectId(),
                    event.getSnapshotId(),
                    event.getSnapshotTime()
            );

        } catch (Exception e) {
            log.error("Failed to start preview", e);
        }
    }
}