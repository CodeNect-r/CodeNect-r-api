package com.lovable.ai_service.dto;

import lombok.*;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UICriticReport {

    private int overallScore;
    private int hierarchyScore;
    private int spacingScore;
    private int consistencyScore;
    private int premiumScore;
    private int responsivenessScore;

    private boolean repairRecommended;

    private List<String> strengths;
    private List<String> weaknesses;
    private List<UIFixSuggestion> suggestions;
}