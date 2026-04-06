package com.lovable.ai_service.validation;

public enum ValidationIssueType {
    MISSING_LOCAL_IMPORT,
    TAILWIND_WIRING,
    CSS_PIPELINE,
    CSS_MISSING_CLASS,
    MISSING_DEP,
    DUPLICATE_COMPONENT,
    NESTED_ROUTER,
    PACKAGE_JSON,
    MISSING_FILE,
    INVALID_TAILWIND_CLASS,
    UNKNOWN
}