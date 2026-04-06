package com.lovable.ai_service.service.impl;

import com.lovable.ai_service.dto.*;
import com.lovable.ai_service.regeneration.ImpactAnalyzer;
import com.lovable.ai_service.service.AiClientService;
import com.lovable.ai_service.service.EmbeddingService;
import com.lovable.ai_service.service.SelfHealingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class SelfHealingServiceImpl implements SelfHealingService {

    private final AiClientService aiClientService;
    private final EmbeddingService embeddingService;

    @Override
    public void handleFeedback(PreviewFeedbackEvent event) {

        PreviewFeedback feedback = event.getFeedback();
        String projectId = event.getProjectId();

        // ✅ CASE 1: BUILD FAILED
        if (!feedback.isBuildSuccess()) {
            log.warn("Build failed → triggering fix");

            fixBuild(projectId, feedback);
            return;
        }

        // ✅ CASE 2: RUNTIME ERROR
        if (!feedback.isRuntimeSuccess()) {
            log.warn("Runtime error → fixing");

            fixRuntime(projectId, feedback);
            return;
        }

        // ✅ CASE 3: HEALTH FAIL
        if (!feedback.isHealthy()) {
            log.warn("App unhealthy → fixing");

            fixHealth(projectId, feedback);
            return;
        }

        log.info("Preview healthy — no action needed");
    }

    private void fixBuild(String projectId, PreviewFeedback feedback) {
        String prompt = """
            Fix build errors in this project.

            BUILD LOGS:
            %s

            Fix only necessary files.
            Return JSON.
            """.formatted(feedback.getBuildLogs());

        regenerate(projectId, prompt);
    }

    private void fixRuntime(String projectId, PreviewFeedback feedback) {
        String prompt = """
            Fix runtime errors.

            ERRORS:
            %s

            Ensure app runs without crashing.
            """.formatted(feedback.getRuntimeErrors());

        regenerate(projectId, prompt);
    }

    private void fixHealth(String projectId, PreviewFeedback feedback) {
        String prompt = """
            App is not responding correctly.

            Fix routing / startup issues.
            """;

        regenerate(projectId, prompt);
    }

    private void regenerate(String projectId, String prompt) {
        // reuse regeneration pipeline
    }
  // private List<GeneratedFile> fixFromVisualFeedback(
//            String projectId,
//            String sessionId,
//            String framework,
//            VisualReport report
//    ) {
//
//        String prompt = """
//        Improve UI based on these issues:
//
//        %s
//
//        Make UI premium and polished.
//        Fix layout, spacing, and hierarchy.
//        """.formatted(report.getIssues());
//
//        ImpactAnalyzer.AnalysisResult analysis =
//                impactAnalyzer.analyze(projectId, prompt);
//
//        return doRegenerate(projectId, prompt, sessionId, framework, analysis);
//    }
}