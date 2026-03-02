package com.lovable.project_service.dto;

import lombok.*;
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiRequestEvent {

    private String eventId;
    private String eventVersion;

    private String projectId;
    private String userEmail;
    private String prompt;

    private String operationType; // CREATE_PROJECT | MODIFY_FILE
}