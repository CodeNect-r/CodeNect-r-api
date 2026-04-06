package com.lovable.project_service.service;

import com.lovable.project_service.dto.*;
import com.lovable.project_service.entity.FileVersion;
import com.lovable.project_service.entity.Project;
import com.lovable.project_service.entity.ProjectFile;
import com.lovable.project_service.entity.ProjectSnapshot;
import com.lovable.project_service.event.PreviewTriggerRequestedEvent;
import com.lovable.project_service.producer.AiRequestProducer;
import com.lovable.project_service.repository.FileVersionRepository;
import com.lovable.project_service.repository.ProjectFileRepository;
import com.lovable.project_service.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final ProjectFileRepository projectFileRepository;
    private final FileVersionRepository fileVersionRepository;
    private final AiRequestProducer aiRequestProducer;
    private final FileVersioningService fileVersioningService;
    private final SnapshotService snapshotService;
    private final ApplicationEventPublisher publisher;

    @Transactional
    public ProjectResponse createProject(String name, String description, String ownerEmail) {

        Project project = Project.builder()
                .name(name)
                .description(description)
                .ownerEmail(ownerEmail)
                .status("PROCESSING")
                .framework("unknown")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        Project saved = projectRepository.save(project);

        String snapshotId = UUID.randomUUID().toString();
        long snapshotTime = System.currentTimeMillis();



        AiRequestEvent event = AiRequestEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventVersion("v1")
                .projectId(saved.getId())
                .userEmail(ownerEmail)
                .prompt(description)
                .framework("unknown")
                .operationType(OperationType.INITIAL_PROJECT)
                .snapshotId(snapshotId)
                .snapshotTime(snapshotTime)
                .parentSnapshotId(null)
                .build();

        aiRequestProducer.send(event);

        return mapToResponse(saved);
    }

    public List<ProjectResponse> myProjects(String email) {
        return projectRepository.findByOwnerEmail(email)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional
    public void handleAiResponse(AiResponseEvent event) {
        Project project = projectRepository.findById(event.getProjectId()).orElseThrow();

        // Ignore outdated snapshot response
        if (project.getLatestSnapshotId() != null
                && !event.getSnapshotId().equals(project.getLatestSnapshotId())) {
            System.out.println("⚠️ Ignoring outdated snapshot: " + event.getSnapshotId());
            return;
        }

        if (event.getFramework() != null) {
            project.setFramework(event.getFramework());
        }

        if (!"COMPLETED".equals(event.getStatus()) || event.getFiles() == null || event.getFiles().isEmpty()) {
            project.setStatus("FAILED");
            project.setUpdatedAt(LocalDateTime.now());
            projectRepository.save(project);

            try {
                snapshotService.markSnapshotFailed(event.getSnapshotId());
            } catch (Exception ignored) {
            }

            return;
        }

        // Save generated files first
        for (GeneratedFile file : event.getFiles()) {
            fileVersioningService.saveOrUpdateFile(
                    project.getId(),
                    file.getPath(),
                    file.getContent()
            );
        }

        // Create snapshot only after files are persisted
        snapshotService.createSnapshot(
                event.getProjectId(),
                event.getSnapshotId(),
                event.getSnapshotTime()
        );

        project.setLatestSnapshotId(event.getSnapshotId());
        project.setStatus("READY");
        project.setUpdatedAt(LocalDateTime.now());
        projectRepository.save(project);

        publisher.publishEvent(
                new PreviewTriggerRequestedEvent(
                        event.getProjectId(),
                        event.getSnapshotId(),
                        event.getSnapshotTime()
                )
        );
    }

    private ProjectResponse mapToResponse(Project project) {
        return ProjectResponse.builder()
                .id(project.getId())
                .name(project.getName())
                .description(project.getDescription())
                .ownerEmail(project.getOwnerEmail())
                .status(project.getStatus())
                .build();
    }

    public List<ProjectFileResponse> getProjectFiles(String projectId) {
        return projectFileRepository.findByProjectId(projectId)
                .stream()
                .map(file -> ProjectFileResponse.builder()
                        .path(file.getFilePath())
                        .currentVersion(file.getCurrentVersion())
                        .content(file.getContent())
                        .build())
                .toList();
    }

    public ProjectFileResponse getFile(String projectId, String path) {
        ProjectFile file = projectFileRepository
                .findByProjectIdAndFilePath(projectId, path)
                .orElseThrow();

        return ProjectFileResponse.builder()
                .path(file.getFilePath())
                .currentVersion(file.getCurrentVersion())
                .content(file.getContent())
                .build();
    }

    public List<FileVersionResponse> getFileVersions(String projectId, String path) {
        return fileVersionRepository
                .findByProjectIdAndFilePathOrderByVersionNumberDesc(projectId, path)
                .stream()
                .map(v -> FileVersionResponse.builder()
                        .versionNumber(v.getVersionNumber())
                        .content(v.getContent())
                        .createdAt(v.getCreatedAt())
                        .build())
                .toList();
    }

    @Transactional
    public void modifyProject(
            String projectId,
            String prompt,
            String userEmail,
            String sessionId
    ) {
        Project project = projectRepository
                .findById(projectId)
                .orElseThrow();

//        if ("unknown".equals(project.getFramework())) {
//            throw new RuntimeException("Project is still initializing. Try again.");
//        }

        project.setStatus("PROCESSING");
        project.setUpdatedAt(LocalDateTime.now());
        projectRepository.save(project);

        String parentSnapshotId = project.getLatestSnapshotId();
        String newSnapshotId = UUID.randomUUID().toString();
        long snapshotTime = System.currentTimeMillis();

        // DO NOT create snapshot here

        AiRequestEvent event = AiRequestEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventVersion("v1")
                .projectId(projectId)
                .userEmail(userEmail)
                .prompt(prompt)
                .sessionId(sessionId)
                .framework(project.getFramework())
                .operationType(OperationType.MODIFY_PROJECT)
                .snapshotId(newSnapshotId)
                .snapshotTime(snapshotTime)
                .parentSnapshotId(parentSnapshotId)
                .build();

        aiRequestProducer.send(event);
    }

    public List<FileNodeResponse> getFileTree(String projectId) {
        List<ProjectFile> files = projectFileRepository.findByProjectId(projectId);
        return FileTreeBuilder.build(files);
    }

    @Transactional
    public void retryProjectGeneration(String projectId, String userEmail) {
        Project project = projectRepository.findById(projectId).orElseThrow();

        if (!project.getOwnerEmail().equals(userEmail)) {
            throw new RuntimeException("Access denied");
        }

        if ("unknown".equals(project.getFramework())) {
            throw new RuntimeException("Project not ready for retry");
        }

        project.setStatus("PROCESSING");
        project.setUpdatedAt(LocalDateTime.now());
        projectRepository.save(project);

        String parentSnapshotId = project.getLatestSnapshotId();
        String snapshotId = UUID.randomUUID().toString();
        long snapshotTime = System.currentTimeMillis();

        // DO NOT create snapshot here

        AiRequestEvent event = AiRequestEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventVersion("v1")
                .projectId(projectId)
                .userEmail(userEmail)
                .prompt(project.getDescription())
                .framework(project.getFramework())
                .operationType(OperationType.RETRY_PROJECT)
                .snapshotId(snapshotId)
                .snapshotTime(snapshotTime)
                .parentSnapshotId(parentSnapshotId)
                .build();

        aiRequestProducer.send(event);
    }

    public List<ProjectFileResponse> getSnapshotFiles(String projectId, String snapshotId) {

        ProjectSnapshot snapshot = snapshotService.getSnapshot(projectId, snapshotId);

        List<FileVersion> versions = fileVersionRepository
                .findByProjectIdAndCreatedAtLessThanEqual(
                        projectId,
                        snapshot.getCreatedAt()
                );

        Map<String, FileVersion> latest = new HashMap<>();

        for (FileVersion v : versions) {
            FileVersion existing = latest.get(v.getFilePath());

            if (existing == null || v.getVersionNumber() > existing.getVersionNumber()) {
                latest.put(v.getFilePath(), v);
            }
        }

        return latest.values().stream()
                .map(v -> ProjectFileResponse.builder()
                        .path(v.getFilePath())
                        .currentVersion(v.getVersionNumber())
                        .content(v.getContent())
                        .build())
                .toList();
    }
}