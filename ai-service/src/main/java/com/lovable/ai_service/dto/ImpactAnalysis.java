package com.lovable.ai_service.dto;

import lombok.*;

import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImpactAnalysis {
    private Set<String> impactedPaths;
}