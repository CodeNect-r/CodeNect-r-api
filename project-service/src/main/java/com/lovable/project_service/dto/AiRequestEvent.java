package com.lovable.project_service.dto;

import lombok.*;
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiRequestEvent {

    private String eventId;
    private String eventVersion;

    private String projectId;
    private String sessionId;

    private String userEmail;
    private String prompt;
    private String framework;

    private OperationType operationType; // CREATE_PROJECT | MODIFY_FILE

    private String snapshotId;
    private long snapshotTime;
    private String parentSnapshotId;
}