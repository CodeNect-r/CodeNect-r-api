package com.lovable.preview_service.Kafka;

import com.lovable.preview_service.dto.PreviewReadyEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PreviewEventProducer {

    private final KafkaTemplate<String, PreviewReadyEvent> kafkaTemplate;

    public void sendPreviewReady(PreviewReadyEvent event) {
        kafkaTemplate.send("preview.ready", event.getProjectId(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to send preview.ready for projectId={}", event.getProjectId(), ex);
                    } else {
                        log.info("preview.ready sent for projectId={}", event.getProjectId());
                    }
                });
    }
}