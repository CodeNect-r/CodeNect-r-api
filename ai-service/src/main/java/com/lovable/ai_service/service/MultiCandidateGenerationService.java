package com.lovable.ai_service.service;

import com.lovable.ai_service.dto.GenerationCandidate;
import com.lovable.ai_service.dto.PromptContext;

import java.util.List;

public interface MultiCandidateGenerationService {
    List<GenerationCandidate> generateCandidates(PromptContext context, String filePath, int count);
}