package com.lovable.preview_service.dto;

import lombok.Data;

@Data
public class PreviewTriggerEvent {
    private String projectId;
    private String snapshotId;
    private long snapshotTime;
}