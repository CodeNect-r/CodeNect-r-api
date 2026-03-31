package com.lovable.preview_service.service;

public interface PartialUpdateHandler {
    void handle(
            String projectId,
            String snapshotId,
            String filePath,
            String content
    );
}