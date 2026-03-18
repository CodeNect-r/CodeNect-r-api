package com.lovable.ai_service.projection;


public interface SimilarDocumentProjection {
    String getContent();
    String getFilePath();

    Double getSimilarity();
}