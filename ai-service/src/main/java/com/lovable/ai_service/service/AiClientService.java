package com.lovable.ai_service.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lovable.ai_service.dto.GeneratedFile;
import com.lovable.ai_service.dto.GenerationMode;
import com.lovable.ai_service.dto.ProjectSpec;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AiClientService {

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;
    private final PromptFactory promptFactory;

    public ProjectSpec planProject(String userPrompt) {
        String response = chatClient.prompt()
                .system(promptFactory.buildPlanningSystemPrompt())
                .user(promptFactory.buildPlanningPrompt(userPrompt))
                .call()
                .content();

        return parseProjectSpecWithRepair(response);
    }

    public GeneratedFile generateSingleFile(
            String context,
            String userPrompt,
            String filePath,
            Set<String> impactedFiles,
            GenerationMode mode,
            String framework
    ) {
        String response = chatClient.prompt()
                .system(promptFactory.buildSingleFileSystemPrompt(mode))
                .user(promptFactory.buildSingleFilePrompt(
                        context,
                        userPrompt,
                        filePath,
                        impactedFiles,
                        mode,
                        framework
                ))
                .call()
                .content();

        return parseSingleFileWithRepair(response, filePath);
    }

    public String summarizeResult(
            String userPrompt,
            String framework,
            List<GeneratedFile> files,
            GenerationMode mode
    ) {
        try {
            return chatClient.prompt()
                    .system(promptFactory.buildSummarySystemPrompt())
                    .user(promptFactory.buildSummaryPrompt(userPrompt, framework, files, mode))
                    .call()
                    .content();
        } catch (Exception e) {
            return fallbackSummary(framework, files, mode);
        }
    }

    private ProjectSpec parseProjectSpecWithRepair(String response) {
        try {
            return objectMapper.readValue(cleanMarkdown(response), ProjectSpec.class);
        } catch (Exception firstFailure) {
            String repaired = chatClient.prompt()
                    .system("You are a JSON repair tool. Return only valid JSON.")
                    .user("""
                    Fix the following invalid JSON into this exact shape:
                    {
                      "framework": "react-vite|next|vue-vite|angular",
                      "files": ["package.json", "src/main.jsx"]
                    }

                    INVALID OUTPUT:
                    %s
                    """.formatted(response))
                    .call()
                    .content();

            try {
                return objectMapper.readValue(cleanMarkdown(repaired), ProjectSpec.class);
            } catch (Exception secondFailure) {
                throw new RuntimeException("ProjectSpec parsing failed after repair attempt");
            }
        }
    }

    private GeneratedFile parseSingleFileWithRepair(String response, String expectedFilePath) {
        try {
            JsonNode root = objectMapper.readTree(cleanMarkdown(response));

            if (root.isObject()) {
                return objectMapper.treeToValue(root, GeneratedFile.class);
            }

            if (root.isArray() && !root.isEmpty()) {
                return objectMapper.treeToValue(root.get(0), GeneratedFile.class);
            }

            throw new RuntimeException("AI returned empty file JSON");
        } catch (Exception firstFailure) {
            String repaired = chatClient.prompt()
                    .system("You are a JSON repair tool. Return only valid JSON.")
                    .user("""
                    Fix the following invalid JSON into a SINGLE file object.
                    Return only:
                    {
                      "path": "%s",
                      "content": "..."
                    }

                    INVALID OUTPUT:
                    %s
                    """.formatted(expectedFilePath, response))
                    .call()
                    .content();

            try {
                JsonNode root = objectMapper.readTree(cleanMarkdown(repaired));

                if (root.isObject()) {
                    return objectMapper.treeToValue(root, GeneratedFile.class);
                }

                if (root.isArray() && !root.isEmpty()) {
                    return objectMapper.treeToValue(root.get(0), GeneratedFile.class);
                }

                throw new RuntimeException("AI returned empty repaired file JSON");
            } catch (Exception secondFailure) {
                throw new RuntimeException("Single-file parsing failed after repair attempt");
            }
        }
    }

    private String fallbackSummary(String framework, List<GeneratedFile> files, GenerationMode mode) {
        List<String> paths = new ArrayList<>();
        for (GeneratedFile file : files) {
            paths.add(file.getPath());
        }

        String action = mode == GenerationMode.INITIAL ? "Built" : "Regenerated";
        return """
        %s the requested frontend in %s with:
        - %d generated files
        - Core app structure and configuration
        - Updated code for the requested changes

        Files:
        - %s
        """.formatted(action, framework, files.size(), String.join("\n- ", paths));
    }

    private String cleanMarkdown(String input) {
        if (input == null) return "";
        return input
                .replace("```json", "")
                .replace("```", "")
                .trim();
    }
}