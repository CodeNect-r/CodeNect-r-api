package com.lovable.ai_service.regeneration;

import com.lovable.ai_service.projection.SimilarDocumentProjection;
import com.lovable.ai_service.repository.DocumentEmbeddingRepository;
import com.lovable.ai_service.service.EmbeddingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ImpactAnalyzer {

    private final EmbeddingService embeddingService;
    private final DocumentEmbeddingRepository repository;
    public Set<String> detectImpactedFiles(String projectId, String userPrompt) {

        float[] vector = embeddingService.generateEmbedding(userPrompt);

        List<SimilarDocumentProjection> similar =
                repository.findTopSimilarByProject(projectId, vector, 5);

        double threshold = 0.75;

        return similar.stream()
                .filter(doc -> doc.getSimilarity() != null
                        && doc.getSimilarity() >= threshold)
                .map(SimilarDocumentProjection::getFilePath)
                .collect(Collectors.toSet());
    }
}