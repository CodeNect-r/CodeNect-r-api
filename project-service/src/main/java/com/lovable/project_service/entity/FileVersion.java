package com.lovable.project_service.entity;


import jakarta.persistence.*;
import lombok.*;
;import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "file_versions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FileVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String projectId;
    private String filePath;
    private int versionNumber;

    @Column(columnDefinition = "text")
    private String content;

    private LocalDateTime createdAt;
}