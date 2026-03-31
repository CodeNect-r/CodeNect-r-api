package com.lovable.preview_service.Kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lovable.preview_service.dto.AiPartialEvent;
import com.lovable.preview_service.service.PreviewStreamBufferService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiPartialListener {


    private final PreviewStreamBufferService bufferService;


    @KafkaListener(topics = "ai.partial", groupId = "preview-service-group-v2")
    public void handlePartial(String message) {

        try {
            AiPartialEvent event =
                    new ObjectMapper().readValue(message, AiPartialEvent.class);

            bufferService.bufferPartialUpdate(
                    event.getProjectId(),
                    event.getSnapshotId(),
                    event.getFilePath(),
                    event.getContent()
            );
            System.out.println("Ai partial event is calling");
        } catch (Exception e) {
            log.error("Partial preview update failed", e);
        }
    }
}