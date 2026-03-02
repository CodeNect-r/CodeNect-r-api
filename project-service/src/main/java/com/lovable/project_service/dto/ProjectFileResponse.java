package com.lovable.project_service.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ProjectFileResponse {

    private String path;
    private int currentVersion;
    private String content;
}