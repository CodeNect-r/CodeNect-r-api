package com.lovable.ai_service.repository;

import com.lovable.ai_service.projection.SimilarDocumentProjection;
import com.lovable.ai_service.entity.DocumentEmbedding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface DocumentEmbeddingRepository
        extends JpaRepository<DocumentEmbedding, UUID> {

    List<DocumentEmbedding> findByProjectId(String projectId);

    @Query(value = """
    SELECT file_path AS filePath,
           1 - (embedding <=> CAST(:embedding AS vector)) AS similarity
    FROM document_embeddings
    WHERE project_id = :projectId
    ORDER BY embedding <=> CAST(:embedding AS vector)
    LIMIT :limit
""", nativeQuery = true)
    List<SimilarDocumentProjection> findTopSimilarByProject(
            @Param("projectId") String projectId,
            @Param("embedding") float[] embedding,
            @Param("limit") int limit
    );
    boolean existsByProjectId(String projectId);

    void deleteByProjectIdAndFilePath(String projectId, String filePath);
    List<DocumentEmbedding> findByProjectIdAndFilePathOrderByChunkIndexAsc(
            String projectId,
            String filePath
    );

}