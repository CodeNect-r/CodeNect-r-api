package com.lovable.project_service.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class FileNodeResponse {
    private String name;
    private String path;
    private String type; // FILE or DIRECTORY
    private Integer currentVersion;
    private Long size;
    private LocalDateTime updatedAt;
    private List<FileNodeResponse> children;
}
