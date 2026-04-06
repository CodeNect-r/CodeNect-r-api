package com.lovable.ai_service.validation.fixer.rules;

import com.lovable.ai_service.dto.GeneratedFile;
import com.lovable.ai_service.validation.FixContext;
import com.lovable.ai_service.validation.ParsedValidationIssue;
import com.lovable.ai_service.validation.ValidationIssueType;
import com.lovable.ai_service.validation.fixer.FixRule;
import com.lovable.ai_service.validation.fixer.PackageJsonSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(30)
@RequiredArgsConstructor
public class MissingDependencyFixRule implements FixRule {

    private final PackageJsonSupport packageJsonSupport;

    @Override
    public boolean supports(ParsedValidationIssue issue) {
        return issue.type() == ValidationIssueType.MISSING_DEP;
    }

    @Override
    public GeneratedFile apply(ParsedValidationIssue issue, FixContext context) {
        if (issue.packageName() == null || issue.packageName().isBlank()) {
            return packageJsonSupport.fixPackageJson(context.files(), context.framework());
        }
        return packageJsonSupport.addDepToPackageJson(
                context.files(),
                context.framework(),
                issue.packageName()
        );
    }
}