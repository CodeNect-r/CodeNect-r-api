package com.lovable.project_service.entity;

import jakarta.persistence.*;

import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "project_snapshots", indexes = {
        @Index(name = "idx_project_snapshot_time", columnList = "projectId,createdAt")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProjectSnapshot {

    @Id
    private String snapshotId;

    @Column(nullable = false)
    private String projectId;

    @Column(nullable = false)
    private long snapshotTime; // 🔥 IMPORTANT

    private LocalDateTime createdAt;

    @Column(nullable = false)
    private String status; // ACTIVE, SUPERSEDED, FAILED
}