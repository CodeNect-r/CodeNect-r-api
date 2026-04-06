package com.lovable.ai_service.validation.fixer.rules;

import com.lovable.ai_service.dto.GeneratedFile;
import com.lovable.ai_service.validation.FixContext;
import com.lovable.ai_service.validation.ParsedValidationIssue;
import com.lovable.ai_service.validation.ValidationIssueType;
import com.lovable.ai_service.validation.fixer.FixRule;
import com.lovable.ai_service.validation.fixer.ProjectFileFixSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(10)
@RequiredArgsConstructor
public class MissingFileFixRule implements FixRule {

    private final ProjectFileFixSupport projectFileFixSupport;

    @Override
    public boolean supports(ParsedValidationIssue issue) {
        return issue.type() == ValidationIssueType.MISSING_FILE;
    }

    @Override
    public GeneratedFile apply(ParsedValidationIssue issue, FixContext context) {
        String path = issue.filePath();
        if (path == null) return null;

        return switch (path) {
            case "index.html" -> projectFileFixSupport.fixIndexHtml(context.framework());
            case "vite.config.js" -> projectFileFixSupport.fixViteConfig(context.framework());
            case "src/main.jsx" -> projectFileFixSupport.fixMainJsx();
            case "app/layout.jsx" -> projectFileFixSupport.fixNextLayout();
            case "app/page.jsx" -> projectFileFixSupport.fixNextPage();
            case "app/globals.css", "src/index.css" -> projectFileFixSupport.fixCssEntryFile(context.files(), context.framework());
            case "tailwind.config.js" -> projectFileFixSupport.fixTailwindConfig(context.framework());
            case "postcss.config.js" -> projectFileFixSupport.fixPostcssConfig(context.framework());
            default -> null;
        };
    }
}