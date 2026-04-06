package com.lovable.ai_service.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class VisualReport {
    private int score;
    private List<String> issues;
    private List<UIFixSuggestion> fixSuggestions;
}
