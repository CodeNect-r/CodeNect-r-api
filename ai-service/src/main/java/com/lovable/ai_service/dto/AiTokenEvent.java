package com.lovable.ai_service.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiTokenEvent {

    private String projectId;
    private String sessionId;
    private String token;
    private boolean completed;
}