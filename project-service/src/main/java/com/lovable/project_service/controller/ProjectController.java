package com.lovable.project_service.controller;


import com.lovable.project_service.dto.CreateProjectRequest;
import com.lovable.project_service.dto.ModifyProjectRequest;
import com.lovable.project_service.dto.ProjectFileResponse;
import com.lovable.project_service.dto.ProjectResponse;
import com.lovable.project_service.service.ProjectService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    @PostMapping
    public ProjectResponse create(
            @Valid @RequestBody CreateProjectRequest request,
            Authentication auth
    ) {
        return projectService.createProject(
                request.getName(),
                request.getDescription(),
                auth.getName()
        );
    }

    @GetMapping("/me")
    public List<ProjectResponse> myProjects(Authentication auth) {
        return projectService.myProjects(auth.getName());
    }

    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public String adminOnly() {
        return "Project admin access granted";
    }

    @GetMapping("/{projectId}/files")
    public List<ProjectFileResponse> getFiles(
            @PathVariable String projectId
    ) {
        return projectService.getProjectFiles(projectId);
    }
    @GetMapping("/{projectId}/files/content")
    public ProjectFileResponse getFile(
            @PathVariable String projectId,
            @RequestParam String path
    ) {
        return projectService.getFile(projectId, path);
    }

    @PostMapping("/{projectId}/ai/modify")
    public void modifyProject(
            @PathVariable String projectId,
            @RequestBody ModifyProjectRequest request,
            Authentication auth
    ) {
        projectService.modifyProject(
                projectId,
                request.getPrompt(),
                auth.getName()
        );
    }
}