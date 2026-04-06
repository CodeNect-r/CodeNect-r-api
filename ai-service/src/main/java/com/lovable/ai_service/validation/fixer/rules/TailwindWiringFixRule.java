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
@Order(40)
@RequiredArgsConstructor
public class TailwindWiringFixRule implements FixRule {

    private final ProjectFileFixSupport projectFileFixSupport;
    private final PackageJsonSupport packageJsonSupport;

    @Override
    public boolean supports(ParsedValidationIssue issue) {
        return issue.type() == ValidationIssueType.TAILWIND_WIRING;
    }

    @Override
    public GeneratedFile apply(ParsedValidationIssue issue, FixContext context) {
        String message = issue.message();

        if (message.contains("missing @import") || message.contains("missing @tailwind")) {
            return projectFileFixSupport.fixCssEntryFile(context.files(), context.framework());
        }
        if (message.contains("vite.config.js")) {
            return projectFileFixSupport.fixViteConfig(context.framework());
        }
        if (message.contains("tailwind.config.js")) {
            return projectFileFixSupport.fixTailwindConfig(context.framework());
        }
        if (message.contains("postcss.config.js")) {
            return projectFileFixSupport.fixPostcssConfig(context.framework());
        }
        if (message.contains("tailwindcss missing")
                || message.contains("@tailwindcss/vite missing")
                || message.contains("autoprefixer missing")
                || message.contains("postcss missing")) {
            return packageJsonSupport.fixPackageJson(context.files(), context.framework());
        }

        return null;
    }
}