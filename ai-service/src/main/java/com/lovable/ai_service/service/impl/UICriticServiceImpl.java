package com.lovable.ai_service.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lovable.ai_service.dto.*;
import com.lovable.ai_service.service.UICriticService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class UICriticServiceImpl implements UICriticService {

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    @Override
    public UICriticReport critique(PromptContext context, List<GeneratedFile> files) {
        try {
            String prompt = buildCritiquePrompt(context, files);

            String response = chatClient.prompt()
                    .system(buildCritiqueSystemPrompt())
                    .user(prompt)
                    .call()
                    .content();

            if (response == null || response.isBlank()) {
                return fallback();
            }

            String cleaned = response.replace("```json", "")
                    .replace("```", "")
                    .trim();

            UICriticReport report = objectMapper.readValue(cleaned, UICriticReport.class);

            if (report.getSuggestions() == null) report.setSuggestions(List.of());
            if (report.getStrengths() == null) report.setStrengths(List.of());
            if (report.getWeaknesses() == null) report.setWeaknesses(List.of());

            return report;
        } catch (Exception e) {
            log.warn("UICritic parse failed: {}", e.getMessage());
            return fallback();
        }
    }

    private String buildCritiqueSystemPrompt() {
        return """
            You are a senior product designer and frontend UI critic.

            Evaluate UI quality from code structure and styling.
            Be strict and premium-oriented.

            Return ONLY valid JSON:
            {
              "overallScore": 0,
              "hierarchyScore": 0,
              "spacingScore": 0,
              "consistencyScore": 0,
              "premiumScore": 0,
              "responsivenessScore": 0,
              "repairRecommended": true,
              "strengths": ["..."],
              "weaknesses": ["..."],
              "suggestions": [
                {
                  "filePath": "src/...",
                  "category": "layout",
                  "issue": "...",
                  "fix": "..."
                }
              ]
            }
            """;
    }

    private String buildCritiquePrompt(PromptContext context, List<GeneratedFile> files) {
        StringBuilder sb = new StringBuilder();

        sb.append("USER REQUEST:\n").append(context.getUserPrompt()).append("\n\n");
        sb.append("FRAMEWORK:\n").append(context.getFramework()).append("\n\n");

        if (context.getIntent() != null) {
            sb.append("INTENT:\n").append(context.getIntent().getPrimaryIntent()).append("\n");
            sb.append("FEATURES:\n").append(context.getIntent().getFeatures()).append("\n\n");
        }

        if (context.getArtifactPlan() != null && context.getArtifactPlan().getArtifacts() != null) {
            sb.append("ARTIFACTS:\n").append(context.getArtifactPlan().getArtifacts()).append("\n\n");
        }

        if (context.getDesignMemory() != null) {
            sb.append("DESIGN MEMORY:\n");
            sb.append("- style: ").append(context.getDesignMemory().getThemeStyle()).append("\n");
            sb.append("- colors: ").append(context.getDesignMemory().getColorSystem()).append("\n");
            sb.append("- radius: ").append(context.getDesignMemory().getRadius()).append("\n");
            sb.append("- shadow: ").append(context.getDesignMemory().getShadow()).append("\n");
            sb.append("- typography: ").append(context.getDesignMemory().getTypography()).append("\n\n");
        }

        sb.append("FILES TO CRITIQUE:\n");
        for (GeneratedFile file : files.stream().limit(8).toList()) {
            if (!isUiFile(file.getPath())) continue;

            sb.append("FILE: ").append(file.getPath()).append("\n");
            String[] lines = file.getContent().split("\n");
            for (int i = 0; i < Math.min(lines.length, 60); i++) {
                sb.append(lines[i]).append("\n");
            }
            sb.append("-----\n");
        }

        sb.append("""
            Evaluate:
            - visual hierarchy
            - spacing rhythm
            - consistency
            - premium feel
            - responsiveness
            - whether the result feels polished or generic
            """);

        return sb.toString();
    }

    private boolean isUiFile(String path) {
        return path.endsWith(".jsx")
                || path.endsWith(".tsx")
                || path.endsWith(".vue")
                || path.endsWith(".css");
    }

    private UICriticReport fallback() {
        return UICriticReport.builder()
                .overallScore(75)
                .hierarchyScore(75)
                .spacingScore(75)
                .consistencyScore(75)
                .premiumScore(75)
                .responsivenessScore(75)
                .repairRecommended(false)
                .strengths(List.of())
                .weaknesses(List.of())
                .suggestions(List.of())
                .build();
    }
}