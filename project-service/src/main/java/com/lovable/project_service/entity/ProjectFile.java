package com.lovable.project_service.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;


@Entity
@Table(name = "project_files",
        indexes = @Index(name = "idx_project_path", columnList = "projectId,path"))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProjectFile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String projectId;

    @Column(nullable = false)
    private String filePath;
    private int currentVersion;


    @Column(columnDefinition = "TEXT")
    private String content;


    private LocalDateTime updatedAt;
}