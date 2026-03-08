package com.lovable.ai_service.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ChatSessionResponse {
    private String id;
    private String projectId;
    private String title;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}