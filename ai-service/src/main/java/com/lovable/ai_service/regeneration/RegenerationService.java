package com.lovable.ai_service.regeneration;

import com.lovable.ai_service.dto.AiProgressEvent;
import com.lovable.ai_service.dto.GeneratedFile;
import com.lovable.ai_service.producer.AiProgressProducer;
import com.lovable.ai_service.service.AiClientService;
import com.lovable.ai_service.service.EmbeddingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class RegenerationService {

    private final ImpactAnalyzer impactAnalyzer;
    private final AiClientService aiClientService;
    private final EmbeddingService embeddingService;
    private final AiProgressProducer progressProducer;

    public List<GeneratedFile> regenerate(
            String projectId,
            String userPrompt
    ) {

        Set<String> impactedFiles =
                impactAnalyzer.detectImpactedFiles(projectId, userPrompt);

        String context = embeddingService.getProjectContext(
                projectId,
                impactedFiles
        );

        List<GeneratedFile> updatedFiles = new ArrayList<>();

        for (String filePath : impactedFiles) {

            progressProducer.send(
                    AiProgressEvent.builder()
                            .projectId(projectId)
                            .filePath(filePath)
                            .status("GENERATING")
                            .build()
            );

            GeneratedFile updated =
                    aiClientService.generateSingleFile(
                            context,
                            userPrompt,
                            filePath
                    );

            embeddingService.storeFileEmbeddings(projectId, updated);

            updatedFiles.add(updated);
        }

        return updatedFiles;
    }
}