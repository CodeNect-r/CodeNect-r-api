package com.lovable.ai_service.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CandidateScore {
    private double totalScore;
    private double uiScore;
    private double buildScore;
    private double consistencyScore;
    private String rationale;
}