package com.lovable.ai_service.regeneration;

import com.lovable.ai_service.service.EmbeddingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * DiffAwarePromptBuilder — updated to use Spring AI VectorStore via EmbeddingService.
 *
 * CHANGES FROM PREVIOUS VERSION:
 *   - Removed DocumentEmbeddingRepository dependency entirely
 *   - loadCurrentContent() now uses EmbeddingService.loadFileContent()
 *   - No more PSQLException from reading the vector column
 *   - No more transaction rollback-only poisoning
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DiffAwarePromptBuilder {

    private final EmbeddingService embeddingService;

    private static final int MAX_FULL_FILE_LINES  = 200;
    private static final int CONTEXT_WINDOW_LINES = 40;

    public String buildDiffAwarePrompt(
            String projectId,
            String filePath,
            String userRequest,
            String framework
    ) {
        String currentContent = loadCurrentContent(projectId, filePath);

        if (currentContent == null || currentContent.isBlank()) {
            log.debug("[DiffAware] No current content for {} — using standard prompt", filePath);
            return buildStandardPrompt(filePath, userRequest, framework);
        }

        String[] lines = currentContent.split("\n");
        if (lines.length > MAX_FULL_FILE_LINES) {
            return buildPartialPrompt(filePath, userRequest, framework, currentContent, lines);
        }

        return buildFullDiffPrompt(filePath, userRequest, framework, currentContent);
    }

    private String buildFullDiffPrompt(
            String filePath, String userRequest, String framework, String currentContent
    ) {
        // Cap at 6000 chars to avoid context length errors
        String capped = currentContent.length() > 6000
                ? currentContent.substring(0, 6000) + "\n// ... truncated"
                : currentContent;

        return """
                You are modifying an EXISTING file. Make ONLY the specific change requested.
                Do NOT rewrite or restructure anything that isn't directly related to the request.
                Preserve all existing imports, logic, styling, and functionality.

                FRAMEWORK: %s
                FILE: %s

                ══════════════════════════════════════════
                CURRENT VERSION (preserve everything here unless changing it):
                ══════════════════════════════════════════
                %s

                ══════════════════════════════════════════
                USER REQUEST (make only this change):
                ══════════════════════════════════════════
                %s

                RULES:
                - Keep all existing imports
                - Keep all existing components and functions
                - Keep all existing Tailwind classes
                - Only add/modify/remove what the request specifically asks for
                - Return the COMPLETE modified file (not just the changed section)
                - Export default must remain on the main component

                Return ONLY valid JSON:
                { "path": "%s", "content": "complete modified file content" }
                """.formatted(framework, filePath, capped, userRequest, filePath);
    }

    private String buildPartialPrompt(
            String filePath, String userRequest, String framework,
            String currentContent, String[] lines
    ) {
        int[] relevantRange   = findRelevantSection(lines, userRequest);
        int   startLine       = relevantRange[0];
        int   endLine         = relevantRange[1];
        String relevantSection = String.join("\n", Arrays.copyOfRange(lines, startLine, endLine + 1));

        log.debug("[DiffAware] Large file {} ({} lines) — sending section lines {}-{}",
                filePath, lines.length, startLine, endLine);

        return """
                You are modifying a SECTION of an existing large file.
                Return ONLY the modified section — it will be spliced back into the full file.

                FRAMEWORK: %s
                FILE: %s (lines %d-%d of %d total)

                ══════════════════════════════════════════
                RELEVANT SECTION:
                ══════════════════════════════════════════
                %s

                ══════════════════════════════════════════
                USER REQUEST:
                ══════════════════════════════════════════
                %s

                RULES:
                - Return ONLY the section shown above, modified as requested
                - Keep all other lines unchanged
                - Preserve all Tailwind classes and JSX structure not related to the request

                Return ONLY valid JSON:
                { "path": "%s", "content": "modified section content only" }
                """.formatted(
                framework, filePath, startLine + 1, endLine + 1, lines.length,
                relevantSection, userRequest, filePath
        );
    }

    private String buildStandardPrompt(String filePath, String userRequest, String framework) {
        return """
                Generate this NEW file for the project.

                FRAMEWORK: %s
                FILE: %s

                USER REQUEST:
                %s

                Generate a complete, production-quality file using Tailwind CSS utility classes.
                Every component must have export default.

                Return ONLY valid JSON:
                { "path": "%s", "content": "complete file content" }
                """.formatted(framework, filePath, userRequest, filePath);
    }

    private int[] findRelevantSection(String[] lines, String userRequest) {
        Set<String> requestWords = Arrays.stream(userRequest.toLowerCase().split("\\W+"))
                .filter(w -> w.length() > 3)
                .filter(w -> !STOP_WORDS.contains(w))
                .collect(Collectors.toSet());

        if (requestWords.isEmpty())
            return new int[]{0, Math.min(lines.length - 1, CONTEXT_WINDOW_LINES * 2)};

        int bestLine  = 0;
        int bestScore = -1;
        for (int i = 0; i < lines.length; i++) {
            String lower = lines[i].toLowerCase();
            int score = 0;
            for (String word : requestWords) if (lower.contains(word)) score++;
            if (score > bestScore) { bestScore = score; bestLine = i; }
        }

        return new int[]{
                Math.max(0, bestLine - CONTEXT_WINDOW_LINES),
                Math.min(lines.length - 1, bestLine + CONTEXT_WINDOW_LINES)
        };
    }

    public String spliceSectionBack(
            String originalContent, String modifiedSection, int startLine, int endLine
    ) {
        String[] lines = originalContent.split("\n");
        List<String> result = new ArrayList<>();
        for (int i = 0; i < startLine; i++) result.add(lines[i]);
        result.addAll(Arrays.asList(modifiedSection.split("\n")));
        for (int i = endLine + 1; i < lines.length; i++) result.add(lines[i]);
        return String.join("\n", result);
    }

    /**
     * Load current file content via EmbeddingService (Spring AI VectorStore).
     * Replaces the previous DocumentEmbeddingRepository call which caused
     * PSQLException (vector column deserialization) and transaction poisoning.
     */
    private String loadCurrentContent(String projectId, String filePath) {
        try {
            return embeddingService.loadFileContent(projectId, filePath);
        } catch (Exception e) {
            log.error("[DiffAware] Failed to load content for {}/{}: {}", projectId, filePath, e.getMessage());
            return null;
        }
    }

    private static final Set<String> STOP_WORDS = Set.of(
            "the", "and", "that", "this", "with", "from", "have", "will",
            "make", "want", "need", "please", "just", "also", "then",
            "should", "would", "could", "into", "your", "file", "code"
    );
}