package com.lovable.ai_service.dto;

import lombok.Builder;

import java.util.Set;

@Builder
public record GenerationProfile(
        boolean premiumRequest,
        boolean enableCandidates,
        boolean enableUiRefinement,
        boolean compactPromptMode,
        int maxConcurrentLlmCalls,
        int validationAttempts,
        int streamDelayMs,
        Set<String> candidateEligibleFiles
) {}