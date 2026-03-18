package com.lovable.ai_service.dto;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@Builder
@NoArgsConstructor // <-- THIS IS CRITICAL FOR THE CONSUMER!
@AllArgsConstructor
public class AiRequestEvent {

    private String projectId;
    private String userEmail;
    private String sessionId;
    private String prompt;
    private String framework;
    private String operationType;
}