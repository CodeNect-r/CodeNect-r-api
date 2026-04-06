package com.lovable.ai_service.service;

import com.lovable.ai_service.dto.*;

import java.util.List;

public interface CandidateJudgeService {

    GenerationCandidate judgeAndSelect(
            PromptContext context,
            List<GenerationCandidate> candidates
    );
}