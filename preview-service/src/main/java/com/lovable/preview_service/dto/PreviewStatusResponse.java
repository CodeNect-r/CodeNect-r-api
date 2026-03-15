package com.lovable.preview_service.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class PreviewStatusResponse {
    private String projectId;
    private String status;
    private Integer port;
    private String url;
    private LocalDateTime updatedAt;
}