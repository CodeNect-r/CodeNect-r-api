package com.lovable.preview_service.Kafka;

import com.lovable.preview_service.dto.AiResponseEvent;
import com.lovable.preview_service.service.PreviewOrchestratorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiResponseListener {

    private final PreviewOrchestratorService orchestrator;

    @KafkaListener(topics = "ai.response", groupId = "preview-service-group")
    public void onAiResponse(AiResponseEvent event) {
        try {
            if (!"COMPLETED".equals(event.getStatus())) {
                return;
            }

            orchestrator.startPreview(event.getProjectId());
        } catch (Exception e) {
            log.error("Failed to start preview for projectId={}", event.getProjectId(), e);
        }
    }
}