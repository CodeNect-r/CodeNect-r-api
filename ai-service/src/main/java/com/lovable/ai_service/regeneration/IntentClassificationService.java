package com.lovable.ai_service.regeneration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lovable.ai_service.service.AiClientService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class IntentClassificationService {

    private final AiClientService aiClientService;
    private final ObjectMapper objectMapper;

    public record IntentResult(
            String intent,
            String styleScope,
            boolean addNewArtifacts,
            boolean modifyExistingFiles,
            boolean updateRouting,
            double confidence
    ) {}

    public IntentResult classify(String userPrompt, String frameworkHint) {
        String prompt = """
                You are an intent classifier for an AI code editor.

                USER REQUEST:
                %s

                FRAMEWORK HINT:
                %s

                Return ONLY valid JSON:
                {
                  "intent": "ADD_FILE | MODIFY | FIX_BUG | RESTYLE | REFACTOR",
                  "styleScope": "GLOBAL | LOCAL | UNKNOWN",
                  "addNewArtifacts": true,
                  "modifyExistingFiles": false,
                  "updateRouting": false,
                  "confidence": 0.0
                }

                RULES:
                - ADD_FILE when user wants new page/component/modal/hook/util/store/api
                - RESTYLE when user wants visual/theme/color/background/font/layout appearance changes
                - FIX_BUG when user mentions bug/error/broken/not working/crash/wrong behavior
                - REFACTOR when user wants restructure/split/cleanup/reorganize
                - MODIFY for targeted content/logic updates that are not bugs or refactors
                - styleScope=GLOBAL only for app-wide/site-wide/theme/global/background requests
                - updateRouting=true only if new pages/routes/views are likely needed
                - Return JSON only
                """.formatted(userPrompt, frameworkHint);

        try {
            String raw = aiClientService.generateRawText(prompt);
            JsonNode node = objectMapper.readTree(raw);

            return new IntentResult(
                    text(node, "intent", "MODIFY"),
                    text(node, "styleScope", "UNKNOWN"),
                    bool(node, "addNewArtifacts", false),
                    bool(node, "modifyExistingFiles", true),
                    bool(node, "updateRouting", false),
                    dbl(node, "confidence", 0.5)
            );
        } catch (Exception e) {
            log.warn("[IntentClassifier] Falling back to heuristic classifier: {}", e.getMessage());
            return new IntentResult("MODIFY", "UNKNOWN", false, true, false, 0.3);
        }
    }

    private String text(JsonNode node, String field, String fallback) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? fallback : v.asText();
    }

    private boolean bool(JsonNode node, String field, boolean fallback) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? fallback : v.asBoolean();
    }

    private double dbl(JsonNode node, String field, double fallback) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? fallback : v.asDouble();
    }
}