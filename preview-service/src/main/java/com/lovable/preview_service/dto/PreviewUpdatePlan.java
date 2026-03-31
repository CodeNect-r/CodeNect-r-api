package com.lovable.preview_service.dto;

import com.lovable.preview_service.entity.ProjectType;
import lombok.Builder;
import lombok.Data;

import java.nio.file.Path;
import java.util.List;

@Data
@Builder
public class PreviewUpdatePlan {
    private PreviewUpdateMode mode;
    private ProjectType projectType;
    private Path workspaceDir;
    private List<String> changedPaths;
    private List<String> deletedPaths;
}