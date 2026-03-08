package com.lovable.ai_service.dto;

import lombok.Data;

@Data
public class CreateChatSessionRequest {
    private String projectId;
    private String title;
}
