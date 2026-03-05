package com.lovable.ai_service.entity;


import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder

@Table(name = "chat_sessions",
        indexes = {
                @Index(name = "idx_project", columnList = "projectId"),
                @Index(name = "idx_user", columnList = "userEmail")
        })
public class ChatSession {

    @Id
    @GeneratedValue
    private UUID id;

    private String projectId;
    private String userEmail;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}