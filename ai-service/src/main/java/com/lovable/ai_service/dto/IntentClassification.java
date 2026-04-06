package com.lovable.ai_service.dto;

import lombok.*;

import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IntentClassification {
    private String primaryIntent;
    private Set<String> features;
}