package com.lovable.ai_service.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenerationCandidate {
    private String candidateId;
    private GeneratedFile file;
    private CandidateScore score;
}