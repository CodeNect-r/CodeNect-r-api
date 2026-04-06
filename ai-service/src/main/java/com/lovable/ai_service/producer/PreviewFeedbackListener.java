package com.lovable.ai_service.producer;

import com.lovable.ai_service.dto.PreviewFeedbackEvent;
import com.lovable.ai_service.service.SelfHealingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class PreviewFeedbackListener {

    private final SelfHealingService selfHealingService;

    @KafkaListener(topics = "preview.feedback", groupId = "ai-service")
    public void handle(String message) {
        try {
            PreviewFeedbackEvent event = parse(message);

            selfHealingService.handleFeedback(event);

        } catch (Exception e) {
            log.error("Failed to process preview feedback", e);
        }
    }

    private PreviewFeedbackEvent parse(String json) {
        // use ObjectMapper
        return null;
    }
}