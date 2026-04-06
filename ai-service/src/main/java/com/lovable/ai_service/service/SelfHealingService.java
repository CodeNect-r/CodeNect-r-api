package com.lovable.ai_service.service;

import com.lovable.ai_service.dto.PreviewFeedbackEvent;

public interface SelfHealingService {
    void handleFeedback(PreviewFeedbackEvent event);
}