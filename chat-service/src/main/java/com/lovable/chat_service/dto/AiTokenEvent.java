package com.lovable.chat_service.dto;

import lombok.Data;

@Data
public class AiTokenEvent {
    private String projectId;
    private String sessionId;
    private String token;
    private boolean completed;
}