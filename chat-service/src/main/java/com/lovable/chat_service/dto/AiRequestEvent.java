package com.lovable.chat_service.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AiRequestEvent {
    private String eventId;
    private String eventVersion;
    private String projectId;
    private String userEmail;
    private String sessionId;
    private String prompt;
    private OperationType operationType;
}