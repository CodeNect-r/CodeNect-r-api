package com.lovable.ai_service.validation.fixer;

import com.lovable.ai_service.validation.ParsedValidationIssue;
import com.lovable.ai_service.validation.ValidationIssueType;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class IssueParser {

    public ParsedValidationIssue parse(String issue) {
        if (issue == null || issue.isBlank()) {
            return unknown(issue);
        }

        if (issue.startsWith("MISSING_LOCAL_IMPORT:")) {
            return parseMissingLocalImport(issue);
        }
        if (issue.startsWith("TAILWIND_WIRING:")) {
            return ParsedValidationIssue.builder()
                    .type(ValidationIssueType.TAILWIND_WIRING)
                    .raw(issue)
                    .message(issue)
                    .build();
        }
        if (issue.startsWith("CSS_PIPELINE:")) {
            return ParsedValidationIssue.builder()
                    .type(ValidationIssueType.CSS_PIPELINE)
                    .raw(issue)
                    .message(issue)
                    .build();
        }
        if (issue.startsWith("CSS_MISSING_CLASS:")) {
            return ParsedValidationIssue.builder()
                    .type(ValidationIssueType.CSS_MISSING_CLASS)
                    .raw(issue)
                    .message(issue)
                    .build();
        }
        if (issue.startsWith("MISSING_DEP:")) {
            return parseMissingDep(issue);
        }
        if (issue.startsWith("DUPLICATE_COMPONENT:")) {
            return parseDuplicateComponent(issue);
        }
        if (issue.startsWith("NESTED_ROUTER:")) {
            return parseNestedRouter(issue);
        }

        if (issue.toLowerCase().contains("package.json")
                || issue.contains("Missing or invalid script")
                || issue.contains("Missing dependency")
                || issue.contains("Missing devDependency")) {
            return ParsedValidationIssue.builder()
                    .type(ValidationIssueType.PACKAGE_JSON)
                    .raw(issue)
                    .message(issue)
                    .build();
        }

        if (issue.contains("Missing file:")) {
            return parseMissingFile(issue);
        }
        // 🔥 NEW: Tailwind invalid class detection
        if (issue.contains("INVALID_TAILWIND_CLASS")) {

            String filePath = null;

            try {
                filePath = issue.split(":")[1].trim();
            } catch (Exception ignored) {}

            return ParsedValidationIssue.builder()
                    .type(ValidationIssueType.INVALID_TAILWIND_CLASS)
                    .raw(issue)
                    .message(issue)
                    .filePath(filePath)   // ✅ FIX
                    .build();
        }

        return unknown(issue);
    }

    private ParsedValidationIssue parseMissingLocalImport(String issue) {
        String sourcePath = null;
        String targetPath = null;

        try {
            String rest = issue.substring("MISSING_LOCAL_IMPORT:".length()).trim();
            int firstArrow = rest.indexOf("->");
            int secondArrow = rest.lastIndexOf("=>");

            if (firstArrow > 0) {
                sourcePath = rest.substring(0, firstArrow).trim();
            }
            if (secondArrow > 0) {
                targetPath = rest.substring(secondArrow + 2).trim();
            }
        } catch (Exception ignored) {
        }

        Map<String, String> metadata = new HashMap<>();
        if (sourcePath != null) metadata.put("sourcePath", sourcePath);
        if (targetPath != null) metadata.put("targetPath", targetPath);

        return ParsedValidationIssue.builder()
                .type(ValidationIssueType.MISSING_LOCAL_IMPORT)
                .raw(issue)
                .filePath(sourcePath)
                .secondaryPath(targetPath)
                .message(issue)
                .metadata(metadata)
                .build();
    }

    private ParsedValidationIssue parseMissingDep(String issue) {
        String pkgName = null;
        int idx = issue.indexOf("imports '");
        if (idx >= 0) {
            int start = idx + 9;
            int end = issue.indexOf("'", start);
            if (end > start) pkgName = issue.substring(start, end);
        }

        return ParsedValidationIssue.builder()
                .type(ValidationIssueType.MISSING_DEP)
                .raw(issue)
                .packageName(pkgName)
                .message(issue)
                .build();
    }

    private ParsedValidationIssue parseDuplicateComponent(String issue) {
        int firstColon = issue.indexOf(':');
        int secondColon = issue.indexOf(':', firstColon + 1);

        String filePath = null;
        String componentName = null;

        if (firstColon >= 0 && secondColon >= 0) {
            filePath = issue.substring(firstColon + 1, secondColon);
            componentName = issue.substring(secondColon + 1).trim();
        }

        return ParsedValidationIssue.builder()
                .type(ValidationIssueType.DUPLICATE_COMPONENT)
                .raw(issue)
                .filePath(filePath)
                .symbolName(componentName)
                .message(issue)
                .build();
    }

    private ParsedValidationIssue parseNestedRouter(String issue) {
        String trimmed = issue.replace("NESTED_ROUTER:", "").trim();
        String filePath = trimmed.split(" ")[0];

        return ParsedValidationIssue.builder()
                .type(ValidationIssueType.NESTED_ROUTER)
                .raw(issue)
                .filePath(filePath)
                .message(issue)
                .build();
    }

    private ParsedValidationIssue parseMissingFile(String issue) {
        String filePath = issue.substring(issue.indexOf("Missing file:") + "Missing file:".length()).trim();
        return ParsedValidationIssue.builder()
                .type(ValidationIssueType.MISSING_FILE)
                .raw(issue)
                .filePath(filePath)
                .message(issue)
                .build();
    }

    private ParsedValidationIssue unknown(String issue) {
        return ParsedValidationIssue.builder()
                .type(ValidationIssueType.UNKNOWN)
                .raw(issue)
                .message(issue)
                .build();
    }

}