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

@Table(name = "chat_messages",
        indexes = {
                @Index(name = "idx_session", columnList = "session_id")
        })
public class ChatMessage {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private ChatSession session;


    private String role; // USER or AI

    @Column(columnDefinition = "TEXT")
    private String content;

    private LocalDateTime createdAt;
}