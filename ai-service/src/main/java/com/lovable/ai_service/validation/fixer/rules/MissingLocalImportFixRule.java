package com.lovable.ai_service.validation.fixer.rules;

import com.lovable.ai_service.dto.GeneratedFile;
import com.lovable.ai_service.dto.GenerationMode;
import com.lovable.ai_service.dto.PromptContext;
import com.lovable.ai_service.service.AiClientService;
import com.lovable.ai_service.validation.FixContext;
import com.lovable.ai_service.validation.ParsedValidationIssue;
import com.lovable.ai_service.validation.ValidationIssueType;
import com.lovable.ai_service.validation.fixer.FixRule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

@Component
@Order(80)

@RequiredArgsConstructor
@Slf4j
public class MissingLocalImportFixRule implements FixRule {

    private final AiClientService aiClientService;

    @Override
    public boolean supports(ParsedValidationIssue issue) {
        return issue.type() == ValidationIssueType.MISSING_LOCAL_IMPORT;
    }

    @Override
    public GeneratedFile apply(ParsedValidationIssue issue, FixContext context) {
        String path = issue.secondaryPath();
        if (path == null || path.isBlank()) return null;

        if (context.files().stream().anyMatch(f -> f.getPath().equals(path))) {
            return null;
        }

        if (path.contains("ErrorBoundary")) {
            log.info("🚫 ErrorBoundary import detected — removing importer references");
            return removeErrorBoundaryFromImporter(issue, context);
        }

        String rawContext = buildContext(context);
        String fixPrompt = """
                Fix missing local import by generating the missing file.

                Missing file:
                %s

                Original user request:
                %s

                Rules:
                - create exactly the missing file
                - match existing project style
                - valid imports only
                - export default if component file
                """.formatted(path, context.userPrompt());

        PromptContext ctx = PromptContext.builder()
                .projectId(null)
                .sessionId(null)
                .userPrompt(fixPrompt)
                .mode(GenerationMode.REGENERATE)
                .framework(context.framework())
                .rawContext(rawContext)
                .targetFiles(context.files().stream()
                        .map(GeneratedFile::getPath)
                        .collect(Collectors.toSet()))
                .impactedFiles(Set.of(path))
                .build();

        GeneratedFile generated = aiClientService.generateSingleFile(ctx, path);
        if (generated == null || generated.getContent() == null || generated.getContent().isBlank()) {
            return fallbackComponent(path);
        }

        return generated;
    }

    private String buildContext(FixContext context) {
        StringBuilder sb = new StringBuilder();
        for (GeneratedFile file : context.files().stream().limit(8).toList()) {
            sb.append("FILE: ").append(file.getPath()).append("\n");
            String[] lines = file.getContent().split("\n");
            for (int i = 0; i < Math.min(lines.length, 20); i++) {
                sb.append(lines[i]).append("\n");
            }
            sb.append("-----\n");
        }
        return sb.toString();
    }

    private GeneratedFile removeErrorBoundaryFromImporter(ParsedValidationIssue issue, FixContext context) {
        String importerPath = issue.filePath();
        if (importerPath == null) return null;

        GeneratedFile importer = context.files().stream()
                .filter(f -> f.getPath().equals(importerPath))
                .findFirst()
                .orElse(null);

        if (importer == null) return null;

        String content = importer.getContent()
                .replaceAll("(?m)^import\\s+ErrorBoundary\\s+from\\s+['\"].+['\"];?\\n?", "")
                .replaceAll("</?ErrorBoundary>", "");

        return GeneratedFile.builder()
                .path(importerPath)
                .content(content)
                .build();
    }

    private GeneratedFile fallbackComponent(String path) {
        String componentName = path.replaceAll("^.*?/([A-Za-z0-9_]+)\\.(jsx|tsx|js|ts)$", "$1");
        if (componentName.equals(path)) componentName = "FallbackComponent";

        String content = """
                export default function %s() {
                  return <div>Placeholder</div>;
                }
                """.formatted(componentName);

        return GeneratedFile.builder()
                .path(path)
                .content(content)
                .build();
    }
}