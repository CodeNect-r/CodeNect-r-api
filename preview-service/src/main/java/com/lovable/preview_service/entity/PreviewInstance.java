package com.lovable.preview_service.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "preview_instances")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PreviewInstance {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    private String projectId;

    private String containerId;

    private String containerName;

    private String imageTag;

    private String status;
    // BUILDING / RUNNING / STOPPED / FAILED
    private String currentSnapshotId;

    private Long currentSnapshotTime;

    @Enumerated(EnumType.STRING)
    private ProjectType currentProjectType;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}