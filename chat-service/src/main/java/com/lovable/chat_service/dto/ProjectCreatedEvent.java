package com.lovable.chat_service.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectCreatedEvent {

    private String requestId;

    private String projectId;

    private String userEmail;

}