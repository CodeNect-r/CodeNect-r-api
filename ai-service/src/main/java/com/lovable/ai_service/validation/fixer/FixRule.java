package com.lovable.ai_service.validation.fixer;

import com.lovable.ai_service.dto.GeneratedFile;
import com.lovable.ai_service.validation.FixContext;
import com.lovable.ai_service.validation.ParsedValidationIssue;

public interface FixRule {
    boolean supports(ParsedValidationIssue issue);
    GeneratedFile apply(ParsedValidationIssue issue, FixContext context);
}