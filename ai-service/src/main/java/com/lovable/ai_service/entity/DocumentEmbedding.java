package com.lovable.ai_service.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "document_embeddings",
        indexes = {
                @Index(name = "idx_project_id", columnList = "projectId"),
                @Index(name = "idx_project_file", columnList = "projectId,filePath")
        })
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentEmbedding {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String projectId;

    @Column(nullable = false)
    private String filePath;     // NEW

    @Column(nullable = false)
    private int chunkIndex;      // NEW

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(columnDefinition = "vector(1024)")
    private float[] embedding;
}