package com.lovable.preview_service.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "preview_file_manifest",
        indexes = {
                @Index(name = "idx_preview_manifest_project", columnList = "projectId"),
                @Index(name = "idx_preview_manifest_project_path", columnList = "projectId,filePath", unique = true)
        }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PreviewFileManifest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    private String projectId;

    private String filePath;

    private int currentVersion;

    private String contentHash;

    private LocalDateTime updatedAt;
}