package com.lovable.project_service.event;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PreviewTriggerRequestedEvent {
    private final String projectId;
    private final String snapshotId;
    private final long snapshotTime;
}