package com.lovable.ai_service.service;

import com.lovable.ai_service.dto.GeneratedFile;
import com.lovable.ai_service.entity.DocumentEmbedding;
import com.lovable.ai_service.repository.DocumentEmbeddingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class EmbeddingService {

    private final EmbeddingModel embeddingModel;
    private final DocumentEmbeddingRepository embeddingRepository;

    private static final int CHUNK_SIZE = 800; // characters

    public float[] generateEmbedding(String text) {
        return embeddingModel
                .embedForResponse(List.of(text))
                .getResults()
                .get(0)
                .getOutput();
    }

    public void storeFileEmbeddings(String projectId, GeneratedFile file) {

        // Delete old embeddings for this file
        embeddingRepository.deleteByProjectIdAndFilePath(projectId, file.getPath());

        List<String> chunks = chunkContent(file.getContent());

        for (int i = 0; i < chunks.size(); i++) {

            float[] vector = generateEmbedding(chunks.get(i));

            DocumentEmbedding embedding = DocumentEmbedding.builder()
                    .projectId(projectId)
                    .filePath(file.getPath())
                    .chunkIndex(i)
                    .content(chunks.get(i))
                    .embedding(vector)
                    .build();

            embeddingRepository.save(embedding);
        }
    }

    private List<String> chunkContent(String content) {

        List<String> chunks = new ArrayList<>();

        for (int i = 0; i < content.length(); i += CHUNK_SIZE) {
            int end = Math.min(content.length(), i + CHUNK_SIZE);
            chunks.add(content.substring(i, end));
        }

        return chunks;
    }
    public String getProjectContext(
            String projectId,
            Set<String> impactedFiles
    ) {

        if (impactedFiles == null || impactedFiles.isEmpty()) {
            return "";
        }

        StringBuilder contextBuilder = new StringBuilder();

        for (String filePath : impactedFiles) {

            List<DocumentEmbedding> chunks =
                    embeddingRepository.findByProjectIdAndFilePathOrderByChunkIndexAsc(
                            projectId,
                            filePath
                    );

            if (chunks.isEmpty()) continue;

            contextBuilder.append("FILE: ")
                    .append(filePath)
                    .append("\n");

            for (DocumentEmbedding chunk : chunks) {
                contextBuilder.append(chunk.getContent())
                        .append("\n");
            }

            contextBuilder.append("\n-------------------------\n");
        }

        return contextBuilder.toString();
    }}