package com.lovable.project_service.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class FileVersionResponse {

    private int versionNumber;
    private String content;
    private LocalDateTime createdAt;
}