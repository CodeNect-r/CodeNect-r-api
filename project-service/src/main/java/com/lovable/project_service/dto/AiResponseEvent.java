package com.lovable.project_service.dto;

import lombok.*;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiResponseEvent {

    private String eventId;
    private String eventVersion;

    private String projectId;
    private String sessionId;

    private List<GeneratedFile> files;
    private String framework;

    private String status;
}