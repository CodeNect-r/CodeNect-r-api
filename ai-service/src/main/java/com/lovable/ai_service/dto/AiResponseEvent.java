package com.lovable.ai_service.dto;


import lombok.Data;

import java.util.List;

@Data
public class AiResponseEvent {

    private String eventId;
    private String eventVersion;

    private String projectId;
    private String sessionId;

    private List<GeneratedFile> files;

    private String status;
}