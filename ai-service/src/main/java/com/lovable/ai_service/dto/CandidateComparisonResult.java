package com.lovable.ai_service.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CandidateComparisonResult {

    private String winnerId;   // candidate-1 or candidate-2
    private String reasoning;

    private int uiScoreA;
    private int uiScoreB;

    private int usabilityScoreA;
    private int usabilityScoreB;

    private int premiumScoreA;
    private int premiumScoreB;
}