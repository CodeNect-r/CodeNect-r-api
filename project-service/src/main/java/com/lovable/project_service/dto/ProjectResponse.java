package com.lovable.project_service.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ProjectResponse {

    private String id;
    private String name;
    private String description;
    private String ownerEmail;
    private String status;
}