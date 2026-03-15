package com.lovable.preview_service.Kafka;

import com.lovable.preview_service.dto.AiResponseEvent;
import com.lovable.preview_service.service.PreviewOrchestratorService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AiResponseListener {

    private final PreviewOrchestratorService orchestrator;

    @KafkaListener(topics = "ai.response")
    public void onAiResponse(AiResponseEvent event) throws Exception {
        if (!"COMPLETED".equalsIgnoreCase(event.getStatus())) {
            return;
        }
        if(orchestrator.isPreviewRunning(event.getProjectId())){
            orchestrator.updatePreviewFiles(event.getProjectId());
        }else{
            orchestrator.startPreview(event.getProjectId());
        }
    }
}