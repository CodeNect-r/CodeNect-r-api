package com.lovable.preview_service.service;

import com.lovable.preview_service.Kafka.PreviewEventProducer;
import com.lovable.preview_service.Repository.PreviewInstanceRepository;
import com.lovable.preview_service.dto.PreviewReadyEvent;
import com.lovable.preview_service.dto.PreviewStatusResponse;
import com.lovable.preview_service.entity.PreviewInstance;
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
public class PreviewOrchestratorService {

    private final PreviewInstanceRepository repository;
    private final DockerService dockerService;
    private final BuildService buildService;
    private final NginxService nginxService;
    private final PreviewEventProducer previewEventProducer;

    @Transactional
    public String startPreview(String projectId) throws Exception {
        Optional<PreviewInstance> existingOpt = repository.findWithLockByProjectId(projectId);

        if (existingOpt.isPresent()) {
            PreviewInstance existing = existingOpt.get();

            if ("RUNNING".equals(existing.getStatus())) {
                return previewUrl(projectId);
            }

            cleanupExistingInstance(existing);
            repository.delete(existing);
            repository.flush();
        }

        String containerName = dockerService.containerName(projectId);

        PreviewInstance instance = PreviewInstance.builder()
                .projectId(projectId)
                .containerName(containerName)
                .status("BUILDING")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        repository.saveAndFlush(instance);

        String containerId = null;

        try {
            Path buildDir = buildService.prepareBuildDirectory(projectId);

            String imageTag = dockerService.buildImage(projectId, buildDir);
            containerId = dockerService.runContainer(projectId, imageTag);
            dockerService.waitForHttpHealthy(projectId, containerName);

            String domain = projectId + ".localhost";
            nginxService.createDomainRouting(domain, containerName);

            instance.setImageTag(imageTag);
            instance.setContainerId(containerId);
            instance.setStatus("RUNNING");
            instance.setUpdatedAt(LocalDateTime.now());

            repository.saveAndFlush(instance);

            String url = previewUrl(projectId);
            previewEventProducer.sendPreviewReady(new PreviewReadyEvent(projectId, url));

            return url;

        } catch (Exception e) {
            safeCleanup(projectId, containerId, containerName);

            instance.setStatus("FAILED");
            instance.setUpdatedAt(LocalDateTime.now());
            repository.saveAndFlush(instance);

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
                .map(p -> "RUNNING".equals(p.getStatus()))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public PreviewStatusResponse getPreviewStatus(String projectId) {
        return repository.findByProjectId(projectId)
                .map(instance -> PreviewStatusResponse.builder()
                        .projectId(projectId)
                        .status(instance.getStatus())
                        .url("RUNNING".equals(instance.getStatus()) ? previewUrl(projectId) : null)
                        .updatedAt(instance.getUpdatedAt())
                        .build())
                .orElse(PreviewStatusResponse.builder()
                        .projectId(projectId)
                        .status("NOT_FOUND")
                        .build());
    }

    private void cleanupExistingInstance(PreviewInstance instance) {
        try {
            if (instance.getContainerId() != null && !instance.getContainerId().isBlank()) {
                dockerService.removeContainer(instance.getProjectId(), instance.getContainerId());
            } else if (instance.getContainerName() != null && !instance.getContainerName().isBlank()) {
                dockerService.removeContainerIfExists(instance.getProjectId(), instance.getContainerName());
            }
        } catch (Exception e) {
            log.warn("Failed cleaning old preview container for projectId={}", instance.getProjectId(), e);
        }

        try {
            nginxService.removeDomainRouting(instance.getProjectId() + ".localhost");
        } catch (Exception e) {
            log.warn("Failed cleaning old nginx routing for projectId={}", instance.getProjectId(), e);
        }
    }

    private void safeCleanup(String projectId, String containerId, String containerName) {
        try {
            if (containerId != null && !containerId.isBlank()) {
                dockerService.removeContainer(projectId, containerId);
            } else {
                dockerService.removeContainerIfExists(projectId, containerName);
            }
        } catch (Exception e) {
            log.warn("Container cleanup failed for projectId={}", projectId, e);
        }

        try {
            nginxService.removeDomainRouting(projectId + ".localhost");
        } catch (Exception e) {
            log.warn("Nginx cleanup failed for projectId={}", projectId, e);
        }
    }

    private String previewUrl(String projectId) {
        return "http://" + projectId + ".localhost";
    }
}