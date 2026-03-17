package com.lovable.chat_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ChatRequest {

    private String requestId;   // important for first prompt idempotency

    private String projectId;

    private String sessionId;

    @NotBlank(message = "prompt is required")
    @Size(max = 10000, message = "prompt is too long")
    private String prompt;
}