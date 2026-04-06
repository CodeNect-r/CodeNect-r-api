package com.lovable.ai_service.service.impl;

import com.lovable.ai_service.dto.*;
import com.lovable.ai_service.service.AiClientService;
import com.lovable.ai_service.service.MultiCandidateGenerationService;
import com.lovable.ai_service.service.UICriticService;
import com.lovable.ai_service.validation.BuildValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class MultiCandidateGenerationServiceImpl implements MultiCandidateGenerationService {

    private final AiClientService aiClientService;
    private final BuildValidator buildValidator;
    private final UICriticService uiCriticService;

    @Override
    public List<GenerationCandidate> generateCandidates(PromptContext context, String filePath, int count) {
        List<GenerationCandidate> results = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            PromptContext candidateContext = cloneWithCandidateHint(context, i);

            GeneratedFile file = aiClientService.generateSingleFile(candidateContext, filePath);
            GeneratedFile repaired = buildValidator.repairFile(file, context.getFramework());

            UICriticReport critique = uiCriticService.critique(
                    candidateContext,
                    List.of(repaired)
            );

            CandidateScore score = CandidateScore.builder()
                    .uiScore(critique.getOverallScore())
                    .buildScore(computeBuildScore(repaired))
                    .consistencyScore(computeConsistencyScore(candidateContext, repaired))
                    .totalScore(
                            critique.getOverallScore() * 0.6
                                    + computeBuildScore(repaired) * 0.25
                                    + computeConsistencyScore(candidateContext, repaired) * 0.15
                    )
                    .rationale("Candidate " + (i + 1) + " scored by UI/build/consistency")
                    .build();

            results.add(GenerationCandidate.builder()
                    .candidateId("candidate-" + (i + 1))
                    .file(repaired)
                    .score(score)
                    .build());
        }

        return results;
    }

    private PromptContext cloneWithCandidateHint(PromptContext context, int variantIndex) {
        String candidateHint = switch (variantIndex) {
            case 0 -> "Create the strongest premium and balanced version.";
            case 1 -> "Create a more ambitious, visually bold premium version.";
            default -> "Create a polished premium version.";
        };

        return PromptContext.builder()
                .projectId(context.getProjectId())
                .sessionId(context.getSessionId())
                .userPrompt(context.getUserPrompt() + "\n\nCANDIDATE STRATEGY:\n" + candidateHint)
                .mode(context.getMode())
                .framework(context.getFramework())
                .intent(context.getIntent())
                .artifactPlan(context.getArtifactPlan())
                .impactAnalysis(context.getImpactAnalysis())
                .designMemory(context.getDesignMemory())
                .existingFiles(context.getExistingFiles())
                .targetFiles(context.getTargetFiles())
                .impactedFiles(context.getImpactedFiles())
                .rawContext(context.getRawContext())
                .build();
    }

    private double computeBuildScore(GeneratedFile file) {
        if (file == null || file.getContent() == null || file.getContent().isBlank()) {
            return 0;
        }

        double score = 100.0;

        String content = file.getContent();
        if (content.contains("TODO")) score -= 10;
        if (content.contains("Placeholder")) score -= 10;
        if (!content.contains("export default")) score -= 20;
        if (content.length() < 120) score -= 25;

        return Math.max(score, 0);
    }

    private double computeConsistencyScore(PromptContext context, GeneratedFile file) {
        if (context.getDesignMemory() == null || file == null || file.getContent() == null) {
            return 75;
        }

        String content = file.getContent().toLowerCase();
        double score = 70;

        if (context.getDesignMemory().getRadius() != null &&
                content.contains(context.getDesignMemory().getRadius().toLowerCase())) {
            score += 10;
        }

        if (context.getDesignMemory().getShadow() != null &&
                content.contains("shadow")) {
            score += 10;
        }

        if (context.getDesignMemory().getTypography() != null &&
                (content.contains("font") || content.contains("tracking") || content.contains("leading"))) {
            score += 10;
        }

        return Math.min(score, 100);
    }
}