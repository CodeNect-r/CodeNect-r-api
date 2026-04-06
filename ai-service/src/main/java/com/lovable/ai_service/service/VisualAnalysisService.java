package com.lovable.ai_service.service;

import com.lovable.ai_service.dto.PreviewFeedback;
import com.lovable.ai_service.dto.PromptContext;
import com.lovable.ai_service.dto.VisualReport;

public interface VisualAnalysisService {
    VisualReport analyze(PreviewFeedback feedback, PromptContext context);
}