package com.lovable.ai_service.validation;

import com.lovable.ai_service.dto.GeneratedFile;
import com.lovable.ai_service.validation.fixer.FixRule;
import com.lovable.ai_service.validation.fixer.IssueParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class BuildAutoFixer {

    private final IssueParser issueParser;
    private final List<FixRule> fixRules;

    public GeneratedFile fix(
            String issue,
            List<GeneratedFile> files,
            String userPrompt,
            String framework
    ) {
        log.info("🛠 Fixing issue: {}", issue);

        ParsedValidationIssue parsed = issueParser.parse(issue);
        FixContext context = FixContext.builder()
                .files(files)
                .userPrompt(userPrompt)
                .framework(framework)
                .build();

        List<FixRule> orderedRules = new ArrayList<>(fixRules);
        AnnotationAwareOrderComparator.sort(orderedRules);

        for (FixRule rule : orderedRules) {
            if (!rule.supports(parsed)) continue;

            GeneratedFile result = rule.apply(parsed, context);
            if (result != null) {
                log.info("✅ Applied fixer: {} for {}", rule.getClass().getSimpleName(), parsed.type());
                return result;
            }
        }

        log.warn("⚠️ No fixer found for parsed issue type: {}", parsed.type());
        return null;
    }
}