package com.lovable.project_service.service;

import com.lovable.project_service.entity.Project;
import com.lovable.project_service.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProjectAccessService {

    private final ProjectRepository projectRepository;

    public Project getOwnedProject(String projectId, Authentication auth) {

        Project project = projectRepository
                .findById(projectId)
                .orElseThrow(() -> new RuntimeException("Project not found"));

        boolean internal = auth.getAuthorities()
                .stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_INTERNAL"));

        // INTERNAL SERVICE → allow access
        if (internal) {
            return project;
        }

        // USER REQUEST → check ownership
        String userEmail = auth.getName();

        if (!project.getOwnerEmail().equals(userEmail)) {
            throw new RuntimeException("Access denied");
        }

        return project;
    }
}