package com.lovable.project_service.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "project_create_requests", uniqueConstraints = {
        @UniqueConstraint(name = "uk_project_create_request_id", columnNames = "requestId")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProjectCreateRequestRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false, unique = true)
    private String requestId;

    @Column(nullable = false)
    private String userEmail;

    @Column(nullable = false)
    private String projectId;

    @Column(nullable = false)
    private String status; // CREATED, AI_REQUEST_SENT

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}