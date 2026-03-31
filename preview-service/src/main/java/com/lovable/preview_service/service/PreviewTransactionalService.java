package com.lovable.preview_service.service;

import com.lovable.preview_service.Repository.PreviewInstanceRepository;
import com.lovable.preview_service.entity.PreviewInstance;
import com.lovable.preview_service.entity.ProjectType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class PreviewTransactionalService {

    private final PreviewInstanceRepository repository;
    private final DockerService dockerService;

    @Transactional
    public void handlePartialUpdateNow(
            String projectId,
            String snapshotId,
            String filePath,
            String content
    ) {

        PreviewInstance instance = repository
                .findWithLockByProjectId(projectId)
                .orElse(null);

        if (instance == null || !"RUNNING".equals(instance.getStatus())) {
            return;
        }

        if (!snapshotId.equals(instance.getCurrentSnapshotId())) {
            return;
        }

        try {
            dockerService.copyFileIntoContainer(
                    projectId,
                    instance.getContainerId(),
                    instance.getContainerName(),
                    filePath,
                    content,
                    resolveContainerPath(filePath, instance.getCurrentProjectType())
            );

            instance.setUpdatedAt(LocalDateTime.now());
            repository.save(instance);

        } catch (Exception e) {
            log.warn("⚠️ Partial update failed for project={} file={}", projectId, filePath, e);
        }
    }

    private String resolveContainerPath(String filePath, ProjectType type) {
        String normalized = filePath.replace("\\", "/");

        return switch (type) {
            case STATIC -> "/usr/share/nginx/html/" + normalized;
            case NODE -> "/app/" + normalized;
            default -> throw new IllegalStateException("Unsupported type: " + type);
        };
    }
}