package com.lovable.ai_service.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lovable.ai_service.dto.AiTokenEvent;
import com.lovable.ai_service.dto.GeneratedFile;
import com.lovable.ai_service.dto.GenerationMode;
import com.lovable.ai_service.dto.ProjectSpec;
import com.lovable.ai_service.producer.AiTokenProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiClientService {

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;
    private final PromptFactory promptFactory;
    private final AiTokenProducer tokenProducer;

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

        // enforce framework
        if (!framework.equals(spec.getFramework())) {
            log.warn("⚠️ Framework mismatch. Forcing: {}", framework);
            spec.setFramework(framework);
        }

        // FIX #6: enforce CSS-last order immediately after planning
        // so the generation loop never accidentally generates CSS before JSX
        List<String> orderedFiles = promptFactory.sortFilesForGeneration(spec.getFiles());
        spec.setFiles(orderedFiles);
        log.info("📋 File order enforced (CSS last): {}", orderedFiles);

        return spec;
    }

    /* =======================================================
       📄 GENERATE SINGLE FILE (normal files)
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
                                context,
                                userPrompt,
                                filePath,
                                impactedFiles,
                                mode,
                                framework
                        ))
                        .call()
                        .content()
        );

        log.debug("📄 Raw AI File Response: {}", response);

        GeneratedFile file = retryParseSingleFile(response, filePath);

        if (!filePath.equals(file.getPath())) {
            log.warn("⚠️ Fixing incorrect file path from AI: {}", file.getPath());
            file.setPath(filePath);
        }

        return file;
    }

    /* =======================================================
       🎨 GENERATE CSS FILE (FIX #2 + #5)
       Called instead of generateSingleFile() when the current
       file is the CSS entry file (src/index.css, app/globals.css,
       src/style.css, src/styles.css).
       Uses the CSS audit prompt built from already-generated JSX
       so the AI sees the real class names / Tailwind usage.
    ======================================================= */

    public GeneratedFile generateCssFile(
            List<GeneratedFile> alreadyGeneratedFiles,   // FIX #5: raw, not sanitized
            String userPrompt,
            String framework
    ) {
        String cssPath = promptFactory.getCssEntryPath(framework);
        log.info("🎨 Generating CSS entry file with audit prompt: {}", cssPath);

        // FIX #2: use the audit prompt that scans actual JSX content
        String cssAuditPrompt = promptFactory.buildCssAuditPrompt(
                alreadyGeneratedFiles,  // FIX #5: passed raw — no sanitize() applied
                framework,
                userPrompt
        );

        String response = safeContent(
                chatClient.prompt()
                        .system(promptFactory.buildSingleFileSystemPrompt(GenerationMode.INITIAL))
                        .user(cssAuditPrompt)
                        .call()
                        .content()
        );

        log.debug("🎨 Raw CSS Response: {}", response);

        GeneratedFile file = retryParseSingleFile(response, cssPath);

        // Always enforce correct CSS path
        if (!cssPath.equals(file.getPath())) {
            log.warn("⚠️ Fixing incorrect CSS path from AI: {} → {}", file.getPath(), cssPath);
            file.setPath(cssPath);
        }

        return file;
    }

    /* =======================================================
       📊 SUMMARY
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
                            .projectId(projectId)
                            .sessionId(sessionId)
                            .token(token)
                            .completed(false)
                            .build());
                })
                .doOnComplete(() -> {
                    tokenProducer.send(AiTokenEvent.builder()
                            .projectId(projectId)
                            .sessionId(sessionId)
                            .token("")
                            .completed(true)
                            .build());
                })
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
                if (root.isObject()) {
                    return objectMapper.treeToValue(root, GeneratedFile.class);
                }
                if (root.isArray() && !root.isEmpty()) {
                    return objectMapper.treeToValue(root.get(0), GeneratedFile.class);
                }
            } catch (Exception e) {
                log.warn("⚠️ File parse failed. Attempt {}", i + 1);
            }
            current = repairJson(current, expectedFilePath);
        }
        throw new RuntimeException("❌ Single-file parsing failed after retries");
    }

    /* =======================================================
       🔧 JSON REPAIR
    ======================================================= */

    private String repairJson(String invalidJson, String context) {
        log.info("🛠 Repairing JSON...");
        return safeContent(
                chatClient.prompt()
                        .system("You are a strict JSON repair tool. Return ONLY valid JSON.")
                        .user("""
                        Fix this invalid JSON.
                        CONTEXT: %s
                        INVALID JSON: %s
                        """.formatted(context, invalidJson))
                        .call()
                        .content()
        );
    }

    /* =======================================================
       🛡 SAFETY
    ======================================================= */

    private String safeContent(String content) {
        if (content == null || content.trim().isEmpty()) {
            throw new RuntimeException("❌ AI returned empty response");
        }
        return content;
    }

    private String cleanMarkdown(String input) {
        if (input == null) return "";
        return input.replace("```json", "").replace("```", "").trim();
    }

    /* =======================================================
       📦 FALLBACK SUMMARY
    ======================================================= */

    private String fallbackSummary(String framework, List<GeneratedFile> files, GenerationMode mode) {
        List<String> paths = new ArrayList<>();
        for (GeneratedFile file : files) paths.add(file.getPath());
        String action = mode == GenerationMode.INITIAL ? "Built" : "Regenerated";
        return """
                %s the requested frontend in %s with:
                - %d generated files
                Files:\n- %s
                """.formatted(action, framework, files.size(), String.join("\n- ", paths));
    }
}