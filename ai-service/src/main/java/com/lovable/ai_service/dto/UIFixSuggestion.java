package com.lovable.ai_service.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UIFixSuggestion {
    private String filePath;
    private String category;   // hierarchy, spacing, color, layout, typography
    private String issue;
    private String fix;
}