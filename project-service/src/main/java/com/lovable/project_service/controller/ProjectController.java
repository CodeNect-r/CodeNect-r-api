package com.lovable.project_service.controller;

import com.lovable.project_service.dto.*;
import com.lovable.project_service.service.ProjectAccessService;
import com.lovable.project_service.service.ProjectService;
import com.lovable.project_service.service.ZipDownloadService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;
    private final ProjectAccessService accessService;
    private final ZipDownloadService zipDownloadService;

    @PostMapping
    public ProjectResponse create(@Valid @RequestBody CreateProjectRequest request, Authentication auth) {
        return projectService.createProject(request.getName(), request.getDescription(), auth.getName());
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
    public List<ProjectFileResponse> getFiles(@PathVariable String projectId, Authentication auth) {
        accessService.getOwnedProject(projectId, auth);
        return projectService.getProjectFiles(projectId);
    }

    @GetMapping("/{projectId}/files/tree")
    public List<FileNodeResponse> getFileTree(@PathVariable String projectId, Authentication auth) {
        accessService.getOwnedProject(projectId, auth);
        return projectService.getFileTree(projectId);
    }

    @GetMapping("/{projectId}/files/content")
    public ProjectFileResponse getFile(@PathVariable String projectId,
                                       @RequestParam String path,
                                       Authentication auth) {
        accessService.getOwnedProject(projectId, auth);
        return projectService.getFile(projectId, path);
    }

    @GetMapping("/{projectId}/files/download")
    public ResponseEntity<byte[]> downloadZip(@PathVariable String projectId, Authentication auth) {
        accessService.getOwnedProject(projectId, auth);
        byte[] zip = zipDownloadService.buildProjectZip(projectId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=project-" + projectId + ".zip")
                .body(zip);
    }

    @PostMapping("/{projectId}/ai/modify")
    public void modifyProject(@PathVariable String projectId,
                              @RequestBody ModifyProjectRequest request,
                              Authentication auth) {
        accessService.getOwnedProject(projectId, auth);
        projectService.modifyProject(projectId, request.getPrompt(), auth.getName(),request.getSessionId());
    }

    @PostMapping("/{projectId}/retry")
    public void retryProject(@PathVariable String projectId, Authentication auth) {
        projectService.retryProjectGeneration(projectId, auth.getName());
    }
}
