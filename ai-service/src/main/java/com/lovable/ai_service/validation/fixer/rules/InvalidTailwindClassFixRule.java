package com.lovable.ai_service.validation.fixer.rules;

import com.lovable.ai_service.dto.GeneratedFile;
import com.lovable.ai_service.validation.FixContext;
import com.lovable.ai_service.validation.ParsedValidationIssue;
import com.lovable.ai_service.validation.ValidationIssueType;
import com.lovable.ai_service.validation.fixer.FixRule;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(1)
public class InvalidTailwindClassFixRule implements FixRule {

    @Override
    public boolean supports(ParsedValidationIssue issue) {
        return issue.type() == ValidationIssueType.INVALID_TAILWIND_CLASS;
    }

    @Override
    public GeneratedFile apply(ParsedValidationIssue issue, FixContext context) {

        String filePath = issue.filePath();

        GeneratedFile file = context.files().stream()
                .filter(f -> f.getPath().equals(filePath))
                .findFirst()
                .orElse(null);

        if (file == null) return null;

        String content = file.getContent();

        // 🔥 Replace invalid tokens with safe Tailwind classes
        content = content
                .replace("border-border", "border-gray-800")
                .replace("bg-background", "bg-gray-900")
                .replace("text-foreground", "text-gray-300");

        return GeneratedFile.builder()
                .path(file.getPath())
                .content(content)
                .build();
    }
}