package com.lovable.ai_service.dto;

import lombok.*;

import java.util.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PromptContext {

    private String projectId;
    private String sessionId;

    private String userPrompt;
    private GenerationMode mode;

    private String framework;

    // 🔥 NEW — future-ready fields
    private IntentClassification intent;
    private ArtifactPlan artifactPlan;
    private ImpactAnalysis impactAnalysis;
    private DesignMemory designMemory;

    // Existing project data
    private List<GeneratedFile> existingFiles;
    private Set<String> targetFiles;     // files to generate/update
    private Set<String> impactedFiles;

    // Raw context (temporary — will remove later)
    private String rawContext;

    public String getArtifactsAsString() {
        if (artifactPlan == null || artifactPlan.getArtifacts() == null) return "";
        return String.join(", ", artifactPlan.getArtifacts());
    }

    public String getIntentAsString() {
        return intent != null ? intent.toString() : "";
    }

}