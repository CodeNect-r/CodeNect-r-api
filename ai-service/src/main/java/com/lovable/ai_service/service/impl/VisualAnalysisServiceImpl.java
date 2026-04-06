package com.lovable.ai_service.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lovable.ai_service.dto.PreviewFeedback;
import com.lovable.ai_service.dto.PromptContext;
import com.lovable.ai_service.dto.VisualReport;
import com.lovable.ai_service.service.VisualAnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import static io.jsonwebtoken.lang.Strings.clean;
import static org.springframework.data.jpa.repository.query.QueryEnhancerFactories.fallback;
import static org.springframework.util.StringUtils.truncate;

@Service
@RequiredArgsConstructor
public class VisualAnalysisServiceImpl implements VisualAnalysisService {

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    @Override
    public VisualReport analyze(PreviewFeedback feedback, PromptContext context) {
        try {
            String prompt = """
                Analyze this UI screenshot and app state.

                USER REQUEST:
                %s

                DESIGN SYSTEM:
                %s

                CONSOLE ERRORS:
                %s

                NETWORK ERRORS:
                %s

                DOM:
                %s

                SCREENSHOT (base64):
                %s

                Evaluate:
                - layout quality
                - spacing
                - hierarchy
                - visual polish
                - responsiveness issues
                - broken UI

                Return JSON:
                {
                  "score": 0,
                  "issues": ["..."],
                  "fixSuggestions": [
                    {
                      "filePath": "...",
                      "fix": "..."
                    }
                  ]
                }
                """.formatted(
                    context.getUserPrompt(),
                    context.getDesignMemory(),
                    feedback.getConsoleErrors(),
                    feedback.getNetworkErrors(),
                    truncate(feedback.getDomSnapshot()),
                    feedback.getScreenshotBase64()
            );

            String response = chatClient.prompt()
                    .system("You are a senior UI/UX reviewer.")
                    .user(prompt)
                    .call()
                    .content();

            return objectMapper.readValue(clean(response), VisualReport.class);

        } catch (Exception e) {
            return (VisualReport) fallback();
        }
    }
}