package com.lovable.ai_service.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lovable.ai_service.dto.GeneratedFile;
import com.lovable.ai_service.dto.GenerationMode;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AiClientService {

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;
    private final PromptFactory promptFactory;

    public List<GeneratedFile> generateFiles(
            String context,
            String userPrompt,
            String projectId,
            Set<String> impactedFiles,
            GenerationMode mode
    ) {

        String systemPrompt = promptFactory.buildSystemPrompt(mode);

        String finalPrompt = promptFactory.buildPrompt(
                context,
                userPrompt,
                impactedFiles,
                mode
        );

        String response = chatClient.prompt()
                .system(systemPrompt)
                .user(finalPrompt)
                .call()
                .content();

        return parseWithSelfHealing(response);
    }

    /**
     * Production-safe JSON parsing with one repair attempt.
     */
    private List<GeneratedFile> parseWithSelfHealing(String response) {

        try {
            return objectMapper.readValue(
                    cleanMarkdown(response),
                    new TypeReference<List<GeneratedFile>>() {}
            );
        } catch (Exception firstFailure) {

            // Attempt JSON repair once
            String repairPrompt = """
            The following output is intended to be a JSON array of files but is invalid.

            Fix the JSON and return ONLY valid JSON array.
            Do NOT include explanations.
            Do NOT include markdown.

            INVALID OUTPUT:
            %s
            """.formatted(response);

            String repaired = chatClient.prompt()
                    .system("You are a JSON repair tool. Return only valid JSON.")
                    .user(repairPrompt)
                    .call()
                    .content();

            try {
                return objectMapper.readValue(
                        cleanMarkdown(repaired),
                        new TypeReference<List<GeneratedFile>>() {}
                );
            } catch (Exception secondFailure) {
                throw new RuntimeException(
                        "AI JSON parsing failed after repair attempt"
                );
            }
        }
    }

    /**
     * Removes accidental markdown wrappers like ```json ... ```
     */
    private String cleanMarkdown(String input) {

        if (input == null) return "";

        return input
                .replace("```json", "")
                .replace("```", "")
                .trim();
    }
}