package com.lovable.ai_service.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lovable.ai_service.dto.*;
import com.lovable.ai_service.service.CandidateJudgeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class CandidateJudgeServiceImpl implements CandidateJudgeService {

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    @Override
    public GenerationCandidate judgeAndSelect(
            PromptContext context,
            List<GenerationCandidate> candidates
    ) {
        if (candidates.size() < 2) {
            return candidates.get(0);
        }

        GenerationCandidate a = candidates.get(0);
        GenerationCandidate b = candidates.get(1);

        try {
            String prompt = buildComparisonPrompt(context, a, b);

            String response = chatClient.prompt()
                    .system(buildSystemPrompt())
                    .user(prompt)
                    .call()
                    .content();

            String cleaned = clean(response);

            CandidateComparisonResult result =
                    objectMapper.readValue(cleaned, CandidateComparisonResult.class);

            log.info("AI Judge decision: {}", result.getReasoning());

            if ("candidate-1".equals(result.getWinnerId())) {
                return a;
            } else {
                return b;
            }

        } catch (Exception e) {
            log.warn("AI Judge failed, fallback to numeric scoring");

            return candidates.stream()
                    .max((c1, c2) -> Double.compare(
                            c1.getScore().getTotalScore(),
                            c2.getScore().getTotalScore()
                    ))
                    .orElse(a);
        }
    }

    private String buildSystemPrompt() {
        return """
            You are a senior frontend architect and product designer.

            Compare two UI implementations and select the better one.

            Focus on:
            - visual hierarchy
            - spacing and layout
            - clarity and usability
            - premium SaaS feel
            - maintainability

            Return ONLY valid JSON.
            """;
    }

    private String buildComparisonPrompt(
            PromptContext context,
            GenerationCandidate a,
            GenerationCandidate b
    ) {
        return """
            USER REQUEST:
            %s

            FRAMEWORK:
            %s

            DESIGN MEMORY:
            %s

            ================================
            CANDIDATE A
            ================================
            %s

            ================================
            CANDIDATE B
            ================================
            %s

            ================================
            INSTRUCTIONS
            ================================
            Compare both implementations.

            Choose the better one.

            Return JSON:
            {
              "winnerId": "candidate-1 or candidate-2",
              "reasoning": "...",
              "uiScoreA": 0,
              "uiScoreB": 0,
              "usabilityScoreA": 0,
              "usabilityScoreB": 0,
              "premiumScoreA": 0,
              "premiumScoreB": 0
            }
            """.formatted(
                context.getUserPrompt(),
                context.getFramework(),
                context.getDesignMemory(),
                a.getFile().getContent(),
                b.getFile().getContent()
        );
    }

    private String clean(String input) {
        return input.replace("```json", "")
                .replace("```", "")
                .trim();
    }
}