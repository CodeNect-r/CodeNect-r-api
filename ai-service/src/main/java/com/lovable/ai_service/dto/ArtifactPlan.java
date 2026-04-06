package com.lovable.ai_service.dto;

import lombok.*;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArtifactPlan {
    private List<String> artifacts;
}