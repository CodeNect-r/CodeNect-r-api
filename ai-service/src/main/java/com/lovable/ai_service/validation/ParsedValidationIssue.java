package com.lovable.ai_service.validation;

import lombok.Builder;

import java.util.Map;

@Builder
public record ParsedValidationIssue(
        ValidationIssueType type,
        String raw,
        String filePath,
        String secondaryPath,
        String symbolName,
        String packageName,
        String message,
        Map<String, String> metadata
) {}