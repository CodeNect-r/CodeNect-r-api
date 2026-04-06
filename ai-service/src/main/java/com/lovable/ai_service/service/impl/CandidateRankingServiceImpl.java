package com.lovable.ai_service.service.impl;

import com.lovable.ai_service.dto.*;
import com.lovable.ai_service.service.CandidateRankingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
@Slf4j
public class CandidateRankingServiceImpl implements CandidateRankingService {

    @Override
    public GenerationCandidate rankAndSelect(PromptContext context, List<GenerationCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            throw new IllegalArgumentException("No candidates to rank");
        }

        return candidates.stream()
                .max(Comparator.comparingDouble(c -> c.getScore().getTotalScore()))
                .orElseThrow();
    }
}