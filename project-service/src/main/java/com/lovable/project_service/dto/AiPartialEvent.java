package com.lovable.project_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiPartialEvent {
    private String projectId;
    private String sessionId;
    private String filePath;
    private String content;

    private String snapshotId;
    private long snapshotTime;
}