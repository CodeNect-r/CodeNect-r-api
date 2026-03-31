package com.lovable.project_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PreviewTriggerEvent {
    private String projectId;
    private String snapshotId;
    private long snapshotTime;
}