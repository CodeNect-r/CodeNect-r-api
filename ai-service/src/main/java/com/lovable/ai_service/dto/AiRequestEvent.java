package com.lovable.ai_service.dto;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AiRequestEvent {

    private String projectId;
    private String userEmail;
    private String sessionId;
    private String prompt;
    private String operationType;
}