package com.lovable.ai_service.repository;

import com.lovable.ai_service.entity.DocumentEmbedding;
import com.lovable.ai_service.projection.SimilarDocumentProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DocumentEmbeddingRepository extends JpaRepository<DocumentEmbedding, String> {


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

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query(
            value = "DELETE FROM document_embeddings WHERE project_id = :projectId AND file_path = :filePath",
            nativeQuery = true
    )
    void deleteByProjectIdAndFilePath(
            @Param("projectId") String projectId,
            @Param("filePath") String filePath
    );

    List<DocumentEmbedding> findByProjectIdAndFilePathOrderByChunkIndexAsc(String projectId, String filePath);
}