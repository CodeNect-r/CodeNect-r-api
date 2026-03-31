package com.lovable.ai_service.dto;

import lombok.*;

@Data
@Builder
public class AiPartialEvent {
    private String projectId;
    private String sessionId;
    private String filePath;
    private String content;

    private String snapshotId;
    private long snapshotTime;
}