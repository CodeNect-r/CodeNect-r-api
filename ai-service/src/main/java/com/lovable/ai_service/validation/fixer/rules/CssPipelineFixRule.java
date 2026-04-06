package com.lovable.ai_service.validation.fixer.rules;

import com.lovable.ai_service.dto.GeneratedFile;
import com.lovable.ai_service.validation.FixContext;
import com.lovable.ai_service.validation.ParsedValidationIssue;
import com.lovable.ai_service.validation.ValidationIssueType;
import com.lovable.ai_service.validation.fixer.FixRule;
import com.lovable.ai_service.validation.fixer.PackageJsonSupport;
import com.lovable.ai_service.validation.fixer.ProjectFileFixSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(50)
@RequiredArgsConstructor
public class CssPipelineFixRule implements FixRule {

    private final ProjectFileFixSupport projectFileFixSupport;
    private final PackageJsonSupport packageJsonSupport;

    @Override
    public boolean supports(ParsedValidationIssue issue) {
        return issue.type() == ValidationIssueType.CSS_PIPELINE
                || issue.type() == ValidationIssueType.CSS_MISSING_CLASS;
    }

    @Override
    public GeneratedFile apply(ParsedValidationIssue issue, FixContext context) {
        String message = issue.message();

        if (issue.type() == ValidationIssueType.CSS_MISSING_CLASS) {
            return projectFileFixSupport.fixCssEntryFile(context.files(), context.framework());
        }

        if (message.contains("missing the required directive") || message.contains("Missing CSS entry file")) {
            return projectFileFixSupport.fixCssEntryFile(context.files(), context.framework());
        }
        if (message.contains("vite.config.js does not call tailwindcss()")) {
            return projectFileFixSupport.fixViteConfig(context.framework());
        }
        if (message.contains("postcss.config.js")) {
            return projectFileFixSupport.fixPostcssConfig(context.framework());
        }
        if (message.contains("tailwind.config.js")) {
            return projectFileFixSupport.fixTailwindConfig(context.framework());
        }
        if (message.contains("Tailwind utility classes found")) {
            return packageJsonSupport.fixPackageJson(context.files(), context.framework());
        }

        return null;
    }
}