package com.lovable.ai_service.regeneration;

import com.lovable.ai_service.dto.GeneratedFile;
import com.lovable.ai_service.dto.GenerationMode;
import com.lovable.ai_service.service.AiClientService;
import com.lovable.ai_service.service.EmbeddingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class RegenerationService {

    private final ImpactAnalyzer impactAnalyzer;
    private final AiClientService aiClientService;
    private final EmbeddingService embeddingService;

    public List<GeneratedFile> regenerate(
            String projectId,
            String userPrompt
    ) {

        // 1️⃣ Detect impacted files using embeddings
        Set<String> impactedFiles =
                impactAnalyzer.detectImpactedFiles(projectId, userPrompt);

        // 2️⃣ Build context from those files
        String context = embeddingService.getProjectContext(
                projectId,
                impactedFiles
        );

        // 3️⃣ Call AI in REGENERATE mode
        List<GeneratedFile> updatedFiles =
                aiClientService.generateFiles(
                        context,
                        userPrompt,
                        projectId,
                        impactedFiles,
                        GenerationMode.REGENERATE
                );

        // 4️⃣ Re-embed updated files
        for (GeneratedFile file : updatedFiles) {
            embeddingService.storeFileEmbeddings(projectId, file);
        }

        return updatedFiles;
    }
}