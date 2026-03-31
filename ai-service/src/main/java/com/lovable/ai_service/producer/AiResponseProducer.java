package com.lovable.ai_service.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lovable.ai_service.dto.AiRequestEvent;
import com.lovable.ai_service.dto.AiResponseEvent;
import com.lovable.ai_service.dto.GeneratedFile;
import com.lovable.ai_service.entity.ChatSession;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class AiResponseProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public void sendResponse(
            AiRequestEvent event,
            ChatSession session,
            List<GeneratedFile> files,
            String framework
    ) {

        try {
            AiResponseEvent response = new AiResponseEvent();

            response.setProjectId(event.getProjectId());
            response.setSessionId(session.getId().toString());
            response.setFiles(files);
            response.setSnapshotId(event.getSnapshotId());
            response.setSnapshotTime(event.getSnapshotTime());
            response.setStatus("COMPLETED");
            response.setFramework(framework);

            kafkaTemplate.send(
                    "ai.response",
                    event.getProjectId(),
                    objectMapper.writeValueAsString(response)
            );

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}