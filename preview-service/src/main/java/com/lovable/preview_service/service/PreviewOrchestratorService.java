package com.lovable.preview_service.service;

import com.lovable.preview_service.Kafka.PreviewEventProducer;
import com.lovable.preview_service.Repository.PreviewInstanceRepository;
import com.lovable.preview_service.dto.PreviewReadyEvent;
import com.lovable.preview_service.dto.PreviewStatusResponse;
import com.lovable.preview_service.dto.PreviewUpdateMode;
import com.lovable.preview_service.dto.PreviewUpdatePlan;
import com.lovable.preview_service.entity.PreviewInstance;
import com.lovable.preview_service.entity.ProjectType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PreviewOrchestratorService implements PartialUpdateHandler {

    private final PreviewInstanceRepository repository;
    private final DockerService dockerService;
    private final BuildService buildService;
    private final NginxService nginxService;
    private final PreviewEventProducer previewEventProducer;
    private final PreviewTransactionalService previewTransactionalService;

    @Transactional
    public String startPreview(String projectId) throws Exception {
        return startPreview(projectId, null, Long.MAX_VALUE);
    }

    @Transactional
    public String startPreview(String projectId, String snapshotId, long snapshotTime) throws Exception {

        Optional<PreviewInstance> existingOpt = repository.findWithLockByProjectId(projectId);
        PreviewInstance existing = existingOpt.orElse(null);

        if (existing != null && isSameOrOlderSnapshot(existing, snapshotId, snapshotTime)) {
            log.info("Skipping preview update for projectId={} snapshotId={} because current snapshot is newer/same",
                    projectId, snapshotId);
            return previewUrl(projectId);
        }

        PreviewUpdatePlan plan = buildService.prepareUpdatePlan(projectId, snapshotId);
        boolean hasRunningPreview = existing != null && "RUNNING".equals(existing.getStatus());

        if (hasRunningPreview && plan.getMode() == PreviewUpdateMode.NO_CHANGES) {
            existing.setCurrentSnapshotId(snapshotId);
            existing.setCurrentSnapshotTime(snapshotTime == Long.MAX_VALUE ? existing.getCurrentSnapshotTime() : snapshotTime);
            existing.setUpdatedAt(LocalDateTime.now());
            repository.saveAndFlush(existing);
            return previewUrl(projectId);
        }

        if (hasRunningPreview && plan.getMode() == PreviewUpdateMode.INCREMENTAL_UPDATE) {
            return applyIncrementalUpdate(existing, snapshotId, snapshotTime, plan);
        }

        return applyFullRebuild(existing, projectId, snapshotId, snapshotTime, plan);
    }

    private String applyIncrementalUpdate(PreviewInstance instance,
                                          String snapshotId,
                                          long snapshotTime,
                                          PreviewUpdatePlan plan) throws Exception {

        ProjectType type = plan.getProjectType();

        if (!(type == ProjectType.STATIC || type == ProjectType.NODE)) {
            return applyFullRebuild(instance, instance.getProjectId(), snapshotId, snapshotTime, plan);
        }

        dockerService.syncIncrementalFiles(
                instance.getProjectId(),
                instance.getContainerId(),
                instance.getContainerName(),
                plan.getWorkspaceDir(),
                plan.getChangedPaths(),
                plan.getDeletedPaths(),
                type
        );

        instance.setCurrentSnapshotId(snapshotId);
        instance.setCurrentSnapshotTime(snapshotTime);
        instance.setCurrentProjectType(type);
        instance.setUpdatedAt(LocalDateTime.now());
        instance.setStatus("RUNNING");
        repository.saveAndFlush(instance);

        previewEventProducer.sendPreviewReady(new PreviewReadyEvent(instance.getProjectId(), previewUrl(instance.getProjectId())));
        log.info("Incremental preview applied for projectId={} snapshotId={} type={}",
                instance.getProjectId(), snapshotId, type);

        return previewUrl(instance.getProjectId());
    }

    @Override
    public void handle(
            String projectId,
            String snapshotId,
            String filePath,
            String content
    ) {
        previewTransactionalService.handlePartialUpdateNow(projectId, snapshotId, filePath, content);
    }

    private String resolveContainerPath(String filePath, ProjectType type) {

        String normalized = filePath.replace("\\", "/");

        return switch (type) {
            case STATIC -> "/usr/share/nginx/html/" + normalized;
            case NODE -> "/app/" + normalized;
            default -> throw new IllegalStateException("Unsupported project type: " + type);
        };
    }
    private String applyFullRebuild(PreviewInstance existing,
                                    String projectId,
                                    String snapshotId,
                                    long snapshotTime,
                                    PreviewUpdatePlan plan) throws Exception {

        boolean hasRunningPreview = existing != null && "RUNNING".equals(existing.getStatus());
        String oldContainerId = null;
        String oldContainerName = null;

        if (hasRunningPreview) {
            oldContainerId = existing.getContainerId();
            oldContainerName = existing.getContainerName();

            existing.setStatus("REBUILDING");
            existing.setUpdatedAt(LocalDateTime.now());
            repository.saveAndFlush(existing);
        }

        String newContainerName = dockerService.containerName(projectId) + "-" + System.currentTimeMillis();
        String newContainerId = null;

        try {
            Path buildDir = plan.getWorkspaceDir();
            String imageTag = dockerService.buildImage(projectId, buildDir);

            newContainerId = dockerService.runContainer(projectId, imageTag, newContainerName);
            dockerService.waitForHttpHealthy(projectId, newContainerName);

            String domain = projectId + ".localhost";
            nginxService.createDomainRouting(domain, newContainerName);

            if (hasRunningPreview) {
                try {
                    dockerService.removeContainer(projectId, oldContainerId);
                } catch (Exception e) {
                    log.warn("Failed to remove old container for projectId={}", projectId, e);
                }
            }

            PreviewInstance instance = existing != null ? existing : new PreviewInstance();
            instance.setProjectId(projectId);
            instance.setContainerName(newContainerName);
            instance.setContainerId(newContainerId);
            instance.setImageTag(imageTag);
            instance.setStatus("RUNNING");
            instance.setCurrentSnapshotId(snapshotId);
            instance.setCurrentSnapshotTime(snapshotTime == Long.MAX_VALUE ? null : snapshotTime);
            instance.setCurrentProjectType(plan.getProjectType());
            if (instance.getCreatedAt() == null) {
                instance.setCreatedAt(LocalDateTime.now());
            }
            instance.setUpdatedAt(LocalDateTime.now());

            repository.saveAndFlush(instance);

            String url = previewUrl(projectId);
            previewEventProducer.sendPreviewReady(new PreviewReadyEvent(projectId, url));

            log.info("Full rebuild preview running for projectId={} snapshotId={} at {}",
                    projectId, snapshotId, url);

            return url;

        } catch (Exception e) {
            log.error("Build failed for projectId={} snapshotId={}", projectId, snapshotId, e);

            safeCleanup(projectId, newContainerId, newContainerName);

            if (hasRunningPreview && existing != null) {
                existing.setStatus("RUNNING");
                existing.setUpdatedAt(LocalDateTime.now());
                repository.saveAndFlush(existing);
                return previewUrl(projectId);
            }

            throw e;
        }
    }

    @Transactional
    public void stopPreview(String projectId) throws Exception {
        PreviewInstance instance = repository.findWithLockByProjectId(projectId).orElseThrow();

        try {
            dockerService.removeContainer(projectId, instance.getContainerId());
        } finally {
            nginxService.removeDomainRouting(projectId + ".localhost");
            instance.setStatus("STOPPED");
            instance.setUpdatedAt(LocalDateTime.now());
            repository.saveAndFlush(instance);
        }
    }

    public boolean isPreviewRunning(String projectId) {
        return repository.findByProjectId(projectId)
                .map(p -> "RUNNING".equals(p.getStatus()) || "REBUILDING".equals(p.getStatus()))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public PreviewStatusResponse getPreviewStatus(String projectId) {
        return repository.findByProjectId(projectId)
                .map(instance -> PreviewStatusResponse.builder()
                        .projectId(projectId)
                        .status(instance.getStatus())
                        .url(("RUNNING".equals(instance.getStatus()) || "REBUILDING".equals(instance.getStatus()))
                                ? previewUrl(projectId)
                                : null)
                        .updatedAt(instance.getUpdatedAt())
                        .build())
                .orElse(PreviewStatusResponse.builder()
                        .projectId(projectId)
                        .status("NOT_FOUND")
                        .build());
    }

    private boolean isSameOrOlderSnapshot(PreviewInstance instance, String incomingSnapshotId, long incomingSnapshotTime) {
        if (incomingSnapshotId == null || incomingSnapshotId.isBlank()) {
            return false;
        }

        if (instance.getCurrentSnapshotId() == null || instance.getCurrentSnapshotTime() == null) {
            return false;
        }

        if (incomingSnapshotId.equals(instance.getCurrentSnapshotId())) {
            return true;
        }

        return incomingSnapshotTime <= instance.getCurrentSnapshotTime();
    }

    private void safeCleanup(String projectId, String containerId, String containerName) {
        try {
            if (containerId != null && !containerId.isBlank()) {
                dockerService.removeContainer(projectId, containerId);
            } else if (containerName != null && !containerName.isBlank()) {
                dockerService.removeContainerIfExists(projectId, containerName);
            }
        } catch (Exception e) {
            log.warn("Cleanup failed for projectId={}", projectId, e);
        }
    }

    private String previewUrl(String projectId) {
        return "http://" + projectId + ".localhost";
    }
}