package com.lovable.chat_service.dto;

import lombok.Data;

@Data
public class AiProgressEvent {
    private String projectId;
    private String sessionId;
    private String filePath;
    private String message;
    private String status;
}