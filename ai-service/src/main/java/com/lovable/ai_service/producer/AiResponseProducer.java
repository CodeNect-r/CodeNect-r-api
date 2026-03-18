package com.lovable.ai_service.producer;

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

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void sendResponse(
            AiRequestEvent event,
            ChatSession session,
            List<GeneratedFile> files,
            String framework
    ) {

        AiResponseEvent response = new AiResponseEvent();

        response.setProjectId(event.getProjectId());
        response.setSessionId(session.getId().toString());
        response.setFiles(files);
        response.setStatus("COMPLETED");
        response.setFramework(framework);

        kafkaTemplate.send("ai.response", response);
    }
}