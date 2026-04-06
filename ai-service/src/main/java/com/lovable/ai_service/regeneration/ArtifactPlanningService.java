package com.lovable.ai_service.regeneration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lovable.ai_service.service.AiClientService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ArtifactPlanningService {

    private final AiClientService aiClientService;
    private final ObjectMapper objectMapper;

    public record PlannedArtifact(
            String name,
            String type,
            String purpose
    ) {}

    public List<PlannedArtifact> planArtifacts(String userPrompt, String frameworkHint) {
        String prompt = buildPlanningPrompt(userPrompt, frameworkHint);

        try {
            String raw = aiClientService.generateRawText(prompt);
            return parseArtifacts(raw);
        } catch (Exception e) {
            log.error("[ArtifactPlanning] Failed to plan artifacts: {}", e.getMessage(), e);
            return List.of();
        }
    }

    private String buildPlanningPrompt(String userPrompt, String frameworkHint) {
        return """
                You are an expert software project planner.

                Your task is to read the user's request and extract the NEW artifacts that need to be created.

                FRAMEWORK HINT: %s

                USER REQUEST:
                %s

                Return ONLY valid JSON in this exact format:
                {
                  "artifacts": [
                    {
                      "name": "Checkout",
                      "type": "page",
                      "purpose": "Checkout flow UI"
                    }
                  ]
                }

                RULES:
                - Extract ONLY newly requested files/artifacts to create
                - Allowed types: page, component, modal, dialog, layout, hook, util, helper, api, store
                - If the user requests multiple new things, return all of them
                - Prefer concise artifact names
                - Do not include file paths
                - Do not include explanations outside JSON
                - Do not return existing files to modify
                - If nothing new should be created, return { "artifacts": [] }

                EXAMPLES:

                User: "add login and signup page"
                Output:
                {
                  "artifacts": [
                    { "name": "Login", "type": "page", "purpose": "Login page" },
                    { "name": "Signup", "type": "page", "purpose": "Signup page" }
                  ]
                }

                User: "create cart drawer and wishlist modal"
                Output:
                {
                  "artifacts": [
                    { "name": "CartDrawer", "type": "component", "purpose": "Cart drawer UI" },
                    { "name": "Wishlist", "type": "modal", "purpose": "Wishlist modal" }
                  ]
                }
                """.formatted(frameworkHint, userPrompt);
    }

    private List<PlannedArtifact> parseArtifacts(String raw) {
        List<PlannedArtifact> result = new ArrayList<>();

        try {
            JsonNode root = objectMapper.readTree(raw);
            JsonNode artifacts = root.get("artifacts");

            if (artifacts == null || !artifacts.isArray()) {
                log.warn("[ArtifactPlanning] No artifacts array in planner response");
                return List.of();
            }

            for (JsonNode node : artifacts) {
                String name = text(node, "name");
                String type = text(node, "type");
                String purpose = text(node, "purpose");

                if (name == null || name.isBlank()) continue;
                if (type == null || type.isBlank()) type = "component";

                result.add(new PlannedArtifact(name.trim(), type.trim().toLowerCase(), purpose));
            }

            return result;
        } catch (Exception e) {
            log.error("[ArtifactPlanning] Failed parsing planner JSON: {}", e.getMessage());
            return List.of();
        }
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }
}