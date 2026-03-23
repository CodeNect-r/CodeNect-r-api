package com.lovable.ai_service.projection;

/**
 * Projection for reading file content from document_embeddings
 * without loading the vector column.
 *
 * Used by:
 *   - ImpactAnalyzer.loadAllFileContents()   (import graph analysis)
 *   - ProjectSnapshot.save()                  (snapshot before regeneration)
 */
public interface FileContentProjection {
    String getFilePath();
    Integer getChunkIndex();
    String getContent();
}