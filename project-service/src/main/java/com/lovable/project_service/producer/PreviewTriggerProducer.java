package com.lovable.project_service.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lovable.project_service.dto.PreviewTriggerEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PreviewTriggerProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public void send(PreviewTriggerEvent event) {
        try {
            String json = objectMapper.writeValueAsString(event);

            kafkaTemplate.send("preview.trigger", event.getProjectId(), json)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("Failed to send preview.trigger for projectId={}", event.getProjectId(), ex);
                        } else {
                            log.info("preview.trigger sent for projectId={} snapshotId={}",
                                    event.getProjectId(), event.getSnapshotId());
                        }
                    });

        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize PreviewTriggerEvent", e);
        }
    }
}