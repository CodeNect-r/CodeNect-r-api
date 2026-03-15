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
@Table(name = "chat_sessions", indexes = {
        @Index(name = "idx_chat_project", columnList = "projectId"),
        @Index(name = "idx_chat_user_email", columnList = "userEmail")
})
public class ChatSession {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private String projectId;

    @Column(nullable = false)
    private String userEmail;

    @Column(nullable = false)
    private String title;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
