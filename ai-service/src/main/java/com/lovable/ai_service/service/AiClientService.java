package com.lovable.ai_service.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lovable.ai_service.dto.AiTokenEvent;
import com.lovable.ai_service.dto.GeneratedFile;
import com.lovable.ai_service.dto.GenerationMode;
import com.lovable.ai_service.dto.ProjectSpec;
import com.lovable.ai_service.producer.AiTokenProducer;
import com.lovable.ai_service.validation.BuildValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiClientService {

    private final ChatClient      chatClient;
    private final ObjectMapper    objectMapper;
    private final PromptFactory   promptFactory;
    private final AiTokenProducer tokenProducer;
    private final BuildValidator buildValidator;   // ← injected for per-file repair

    private static final int MAX_RETRIES = 3;

    /* =======================================================
       📐 PLAN PROJECT
    ======================================================= */

    public ProjectSpec planProject(String userPrompt) {

        String framework = promptFactory.detectFramework(userPrompt);

        String response = safeContent(
                chatClient.prompt()
                        .system(promptFactory.buildPlanningSystemPrompt())
                        .user(promptFactory.buildPlanningPrompt(userPrompt, framework))
                        .call()
                        .content()
        );

        log.info("🧠 Planning Response: {}", response);

        ProjectSpec spec = retryParseProjectSpec(response);

        if (!framework.equals(spec.getFramework())) {
            log.warn("⚠️ Framework mismatch. Forcing: {}", framework);
            spec.setFramework(framework);
        }

        // Enforce CSS-last order immediately after planning (FIX #6)
        List<String> orderedFiles = promptFactory.sortFilesForGeneration(spec.getFiles());
        spec.setFiles(orderedFiles);
        log.info("📋 File order enforced (CSS last): {}", orderedFiles);

        return spec;
    }

    /* =======================================================
       📄 GENERATE SINGLE FILE
       Runs BuildValidator.repairFile() on every generated file
       before returning it — catches syntax errors before storage.
    ======================================================= */

    public GeneratedFile generateSingleFile(
            String context,
            String userPrompt,
            String filePath,
            Set<String> impactedFiles,
            GenerationMode mode,
            String framework
    ) {
        log.info("⚡ Generating file: {}", filePath);

        String response = safeContent(
                chatClient.prompt()
                        .system(promptFactory.buildSingleFileSystemPrompt(mode))
                        .user(promptFactory.buildSingleFilePrompt(
                                context, userPrompt, filePath, impactedFiles, mode, framework))
                        .call()
                        .content()
        );

        log.debug("📄 Raw AI File Response: {}", response);

        GeneratedFile file = retryParseSingleFile(response, filePath);

        if (!filePath.equals(file.getPath())) {
            log.warn("⚠️ Fixing incorrect file path from AI: {} → {}", file.getPath(), filePath);
            file.setPath(filePath);
        }

        // ── Pass 1: per-file auto-repair ─────────────────────────
        // Runs deterministic fixes (missing export, @apply custom class,
        // CSS import order, markdown fences, etc.) before storage.
        file = buildValidator.repairFile(file, framework);

        return file;
    }

    public GeneratedFile generateSingleFileWithPrompt(
           String fullPrompt, String filePath, String framework) {
        log.info("⚡ Generating file with custom prompt: {}", filePath);
        String response = safeContent(chatClient.prompt()
                               .system(promptFactory.buildSingleFileSystemPrompt(GenerationMode.REGENERATE))
                               .user(fullPrompt).call().content());
        GeneratedFile file = retryParseSingleFile(response, filePath);
         if (!filePath.equals(file.getPath())) file.setPath(filePath);
        return buildValidator.repairFile(file, framework);
    }

    /* =======================================================
       🎨 GENERATE CSS FILE (FIX #2 + #5)
       Uses the CSS audit prompt built from already-generated JSX
       so the AI sees the real Tailwind class usage.
       Also runs repairFile() after generation.
    ======================================================= */

    public GeneratedFile generateCssFile(
            List<GeneratedFile> alreadyGeneratedFiles,
            String userPrompt,
            String framework
    ) {
        String cssPath = promptFactory.getCssEntryPath(framework);
        log.info("🎨 Generating CSS entry file with audit prompt: {}", cssPath);

        String cssAuditPrompt = promptFactory.buildCssAuditPrompt(
                alreadyGeneratedFiles, framework, userPrompt);

        String response = safeContent(
                chatClient.prompt()
                        .system(promptFactory.buildSingleFileSystemPrompt(GenerationMode.INITIAL))
                        .user(cssAuditPrompt)
                        .call()
                        .content()
        );

        log.debug("🎨 Raw CSS Response: {}", response);

        GeneratedFile file = retryParseSingleFile(response, cssPath);

        if (!cssPath.equals(file.getPath())) {
            log.warn("⚠️ Fixing incorrect CSS path: {} → {}", file.getPath(), cssPath);
            file.setPath(cssPath);
        }

        // ── Pass 1: per-file auto-repair ─────────────────────────
        // Specifically catches: missing @import "tailwindcss", wrong v3
        // directives, @apply custom class names, CSS @import order.
        file = buildValidator.repairFile(file, framework);

        return file;
    }

    /* =======================================================
       📊 SUMMARY (streaming)
    ======================================================= */

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
                            .projectId(projectId).sessionId(sessionId)
                            .token(token).completed(false).build());
                    fullResponse.append(token);
                })
                .doOnComplete(() -> tokenProducer.send(AiTokenEvent.builder()
                        .projectId(projectId).sessionId(sessionId)
                        .token("").completed(true).build()))
                .blockLast();

        return fullResponse.toString();
    }

    /* =======================================================
       🔁 RETRY PARSERS
    ======================================================= */

    private ProjectSpec retryParseProjectSpec(String response) {
        String current = response;
        for (int i = 0; i < MAX_RETRIES; i++) {
            try {
                return objectMapper.readValue(cleanMarkdown(current), ProjectSpec.class);
            } catch (Exception e) {
                log.warn("⚠️ ProjectSpec parse failed. Attempt {}", i + 1);
                current = repairJson(current, "project");
            }
        }
        throw new RuntimeException("❌ ProjectSpec parsing failed after retries");
    }

    private GeneratedFile retryParseSingleFile(String response, String expectedFilePath) {
        String current = response;
        for (int i = 0; i < MAX_RETRIES; i++) {
            try {
                JsonNode root = objectMapper.readTree(cleanMarkdown(current));
                if (root.isObject())
                    return objectMapper.treeToValue(root, GeneratedFile.class);
                if (root.isArray() && !root.isEmpty())
                    return objectMapper.treeToValue(root.get(0), GeneratedFile.class);
            } catch (Exception e) {
                log.warn("⚠️ File parse failed. Attempt {}", i + 1);
            }
            current = repairJson(current, expectedFilePath);
        }
        throw new RuntimeException("❌ Single-file parsing failed after retries for: " + expectedFilePath);
    }

    /* =======================================================
       🔧 JSON REPAIR
    ======================================================= */

    private String repairJson(String invalidJson, String context) {
        log.info("🛠 Repairing JSON for: {}", context);
        return safeContent(
                chatClient.prompt()
                        .system("You are a strict JSON repair tool. Return ONLY valid JSON.")
                        .user("Fix this invalid JSON.\nCONTEXT: %s\nINVALID JSON: %s"
                                .formatted(context, invalidJson))
                        .call()
                        .content()
        );
    }

    /* =======================================================
       🛡 SAFETY HELPERS
    ======================================================= */

    private String safeContent(String content) {
        if (content == null || content.trim().isEmpty())
            throw new RuntimeException("❌ AI returned empty response");
        return content;
    }

    private String cleanMarkdown(String input) {
        if (input == null) return "";
        return input.replace("```json", "").replace("```", "").trim();
    }
}