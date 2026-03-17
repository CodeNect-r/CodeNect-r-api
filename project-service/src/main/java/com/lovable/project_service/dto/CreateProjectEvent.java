package com.lovable.project_service.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateProjectEvent {

    private String requestId;

    private String userEmail;

    private String name;

    private String description;

}