package com.lovable.project_service.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "projects", indexes = {
        @Index(name = "idx_owner_email", columnList = "ownerEmail")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 2000)
    private String description;

    @Column(nullable = false)
    private String ownerEmail;

    @Column(nullable = false)
    private String framework;

    @Column(nullable = false)
    private String status;
    // PROCESSING | READY | FAILED

    @Column(name = "latest_snapshot_id")
    private String latestSnapshotId;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}