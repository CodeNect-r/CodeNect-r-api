package com.lovable.ai_service.validation.fixer.rules;

import com.lovable.ai_service.dto.GeneratedFile;
import com.lovable.ai_service.validation.FixContext;
import com.lovable.ai_service.validation.ParsedValidationIssue;
import com.lovable.ai_service.validation.ValidationIssueType;
import com.lovable.ai_service.validation.fixer.FixRule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(60)

@Slf4j
public class NestedRouterFixRule implements FixRule {

    @Override
    public boolean supports(ParsedValidationIssue issue) {
        return issue.type() == ValidationIssueType.NESTED_ROUTER;
    }

    @Override
    public GeneratedFile apply(ParsedValidationIssue issue, FixContext context) {
        String filePath = issue.filePath();

        GeneratedFile file = context.files().stream()
                .filter(f -> f.getPath().equals(filePath))
                .findFirst()
                .orElse(null);

        if (file == null) {
            log.warn("⚠️ Could not find file to fix nested router: {}", filePath);
            return null;
        }

        String content = file.getContent();

        content = content.replaceAll(
                "(?m)^import\\s+\\{[^}]*(?:BrowserRouter|HashRouter|Router)[^}]*\\}\\s+from\\s+'react-router-dom';?\\n?",
                ""
        );

        if (content.contains("<Routes") && !content.contains("import { Routes")) {
            content = "import { Routes, Route } from 'react-router-dom';\n" + content;
        }

        content = content.replaceAll("<(?:BrowserRouter|HashRouter)>\\s*", "");
        content = content.replaceAll("\\s*</(?:BrowserRouter|HashRouter)>", "");
        content = content.replaceAll("<Router>\\s*", "");
        content = content.replaceAll("\\s*</Router>", "");

        return GeneratedFile.builder()
                .path(filePath)
                .content(content)
                .build();
    }
}