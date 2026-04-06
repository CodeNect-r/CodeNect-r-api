package com.lovable.ai_service.validation.fixer.rules;

import com.lovable.ai_service.dto.GeneratedFile;
import com.lovable.ai_service.validation.ComponentStructureRepairService;
import com.lovable.ai_service.validation.FixContext;
import com.lovable.ai_service.validation.ParsedValidationIssue;
import com.lovable.ai_service.validation.ValidationIssueType;
import com.lovable.ai_service.validation.fixer.FixRule;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(70)

@RequiredArgsConstructor
public class DuplicateComponentFixRule implements FixRule {

    private final ComponentStructureRepairService componentStructureRepairService;

    @Override
    public boolean supports(ParsedValidationIssue issue) {
        return issue.type() == ValidationIssueType.DUPLICATE_COMPONENT;
    }

    @Override
    public GeneratedFile apply(ParsedValidationIssue issue, FixContext context) {
        String filePath = issue.filePath();
        if (filePath == null) return null;

        GeneratedFile file = context.files().stream()
                .filter(f -> f.getPath().equals(filePath))
                .findFirst()
                .orElse(null);

        if (file == null) return null;

        String fixed = componentStructureRepairService.fixDuplicateComponentDeclaration(file.getContent());

        return GeneratedFile.builder()
                .path(filePath)
                .content(fixed)
                .build();
    }
}