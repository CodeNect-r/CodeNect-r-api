package com.lovable.ai_service.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lovable.ai_service.dto.*;
import com.lovable.ai_service.producer.AiTokenProducer;
import com.lovable.ai_service.prompt.PromptFactory;
import com.lovable.ai_service.validation.BuildValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiClientService {

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;
    private final PromptFactory promptFactory;
    private final AiTokenProducer tokenProducer;
    private final BuildValidator buildValidator;

    private static final int MAX_RETRIES = 3;

    public ProjectSpec planProject(String userPrompt) {
        String framework = promptFactory.detectFramework(userPrompt);

        String response = safeContent(
                chatClient.prompt()
                        .system(promptFactory.buildPlanningSystemPrompt())
                        .user(promptFactory.buildPlanningPrompt(userPrompt, framework))
                        .call()
                        .content()
        );

        ProjectSpec spec = retryParseProjectSpec(response);

        if (!framework.equals(spec.getFramework())) {
            spec.setFramework(framework);
        }

        spec.setFiles(promptFactory.sortFilesForGeneration(spec.getFiles()));
        return spec;
    }

    public GeneratedFile generateSingleFile(PromptContext ctx, String filePath) {
        String prompt = promptFactory.buildSingleFilePrompt(ctx, filePath);

        String response = safeContent(
                chatClient.prompt()
                        .system(promptFactory.buildSystemPrompt(ctx.getMode()))
                        .user(prompt)
                        .call()
                        .content()
        );

        GeneratedFile file = retryParseSingleFile(response, filePath);
        return buildValidator.repairFile(file, ctx.getFramework());
    }

    public GeneratedFile generateSingleFileWithPrompt(String fullPrompt, String filePath, String framework) {
        String response = safeContent(
                chatClient.prompt()
                        .system(promptFactory.buildSystemPrompt(GenerationMode.REGENERATE))
                        .user(fullPrompt)
                        .call()
                        .content()
        );

        GeneratedFile file = retryParseSingleFile(response, filePath);
        if (!filePath.equals(file.getPath())) {
            file.setPath(filePath);
        }
        return buildValidator.repairFile(file, framework);
    }

    public GeneratedFile generateCssFile(
            List<GeneratedFile> alreadyGeneratedFiles,
            String userPrompt,
            String framework
    ) {
        String cssPath = promptFactory.getCssEntryPath(framework);
        String cssPrompt = promptFactory.buildCssAuditPrompt(alreadyGeneratedFiles, framework, userPrompt);

        String response = safeContent(
                chatClient.prompt()
                        .system(promptFactory.buildSystemPrompt(GenerationMode.INITIAL))
                        .user(cssPrompt)
                        .call()
                        .content()
        );

        GeneratedFile file = retryParseSingleFile(response, cssPath);
        if (!cssPath.equals(file.getPath())) {
            file.setPath(cssPath);
        }

        return buildValidator.repairFile(file, framework);
    }

    public String streamSummary(
            String userPrompt,
            String framework,
            List<GeneratedFile> files,
            GenerationMode mode,
            String projectId,
            String sessionId
    ) {
        String prompt = promptFactory.buildSummaryPrompt(userPrompt, framework, files, mode);
        StringBuilder fullResponse = new StringBuilder();

        chatClient.prompt()
                .system(promptFactory.buildSummarySystemPrompt())
                .user(prompt)
                .stream()
                .content()
                .doOnNext(token -> {
                    tokenProducer.send(AiTokenEvent.builder()
                            .projectId(projectId)
                            .sessionId(sessionId)
                            .token(token)
                            .completed(false)
                            .build());
                    fullResponse.append(token);
                })
                .doOnComplete(() -> tokenProducer.send(AiTokenEvent.builder()
                        .projectId(projectId)
                        .sessionId(sessionId)
                        .token("")
                        .completed(true)
                        .build()))
                .blockLast();

        return fullResponse.toString();
    }

    private ProjectSpec retryParseProjectSpec(String response) {
        String current = response;
        for (int i = 0; i < MAX_RETRIES; i++) {
            try {
                return objectMapper.readValue(cleanMarkdown(current), ProjectSpec.class);
            } catch (Exception e) {
                log.warn("ProjectSpec parse failed. Attempt {}", i + 1);
                current = repairJson(current, "project");
            }
        }
        throw new RuntimeException("ProjectSpec parsing failed");
    }

    private GeneratedFile retryParseSingleFile(String response, String expectedFilePath) {
        String current = response;
        for (int i = 0; i < MAX_RETRIES; i++) {
            try {
                JsonNode root = objectMapper.readTree(cleanMarkdown(current));

                if (root.isObject()) {
                    return objectMapper.treeToValue(root, GeneratedFile.class);
                }

                if (root.isArray() && !root.isEmpty()) {
                    return objectMapper.treeToValue(root.get(0), GeneratedFile.class);
                }
            } catch (Exception e) {
                log.warn("Single-file parse failed. Attempt {}", i + 1);
            }

            current = repairJson(current, expectedFilePath);
        }

        throw new RuntimeException("Failed to parse file: " + expectedFilePath);
    }

    private String repairJson(String invalidJson, String context) {
        return safeContent(
                chatClient.prompt()
                        .system("Fix JSON. Return ONLY valid JSON.")
                        .user("Context: %s\nJSON: %s".formatted(context, invalidJson))
                        .call()
                        .content()
        );
    }

    private String safeContent(String content) {
        if (content == null || content.isBlank()) {
            throw new RuntimeException("Empty AI response");
        }
        return content;
    }

    private String cleanMarkdown(String input) {
        if (input == null) return "";
        return input.replace("```json", "")
                .replace("```", "")
                .trim();
    }

    public String generateRawText(String prompt) {
        return chatClient.prompt()
                .user(prompt)
                .call()
                .content();
    }
}