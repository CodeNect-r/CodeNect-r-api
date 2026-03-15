package com.lovable.preview_service.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "preview_logs", indexes = @Index(name = "idx_preview_log_project", columnList = "projectId"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PreviewLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String projectId;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String line;

    private LocalDateTime createdAt;
}
