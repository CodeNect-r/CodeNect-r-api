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

        PreviewInstance existing = existingOpt.orElse(null);
        boolean hasRunningPreview = existing != null && "RUNNING".equals(existing.getStatus());

        String oldContainerId = null;
        String oldContainerName = null;


        if (hasRunningPreview) {
            log.info("🔁 Existing preview running → SAFE REBUILD mode");

            oldContainerId = existing.getContainerId();
            oldContainerName = existing.getContainerName();

            // mark rebuilding (DO NOT delete yet)
            existing.setStatus("REBUILDING");
            existing.setUpdatedAt(LocalDateTime.now());
            repository.saveAndFlush(existing);
        }

        String newContainerName = dockerService.containerName(projectId) + "-" + System.currentTimeMillis();
        String newContainerId = null;

        try {
            Path buildDir = buildService.prepareBuildDirectory(projectId);

            String imageTag = dockerService.buildImage(projectId, buildDir);

            // 🚀 RUN NEW CONTAINER (old still running)
            newContainerId = dockerService.runContainer(projectId, imageTag,newContainerName);

            dockerService.waitForHttpHealthy(projectId, newContainerName);

            String domain = projectId + ".localhost";

            // 🔥 SWITCH TRAFFIC to new container
            nginxService.createDomainRouting(domain, newContainerName);

            // ✅ NOW SAFE TO DELETE OLD
            if (hasRunningPreview) {
                try {
                    dockerService.removeContainer(projectId, oldContainerId);
                    repository.delete(existing);
                    repository.flush();

                    log.info("🧹 Old preview removed safely");
                } catch (Exception e) {
                    log.warn("⚠️ Failed to cleanup old preview", e);
                }
            }

            PreviewInstance instance = PreviewInstance.builder()
                    .projectId(projectId)
                    .containerName(newContainerName)
                    .containerId(newContainerId)
                    .imageTag(imageTag)
                    .status("RUNNING")
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

            repository.saveAndFlush(instance);

            String url = previewUrl(projectId);

            previewEventProducer.sendPreviewReady(
                    new PreviewReadyEvent(projectId, url)
            );

            log.info("✅ Preview running at {}", url);

            return url;

        } catch (Exception e) {

            log.error("❌ Build failed → keeping old preview alive", e);

            // cleanup ONLY failed new container
            safeCleanup(projectId, newContainerId, newContainerName);

            if (hasRunningPreview && existing != null) {
                existing.setStatus("RUNNING");
                existing.setUpdatedAt(LocalDateTime.now());
                repository.saveAndFlush(existing);

                return previewUrl(projectId); // ✅ return OLD preview
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
                .map(p -> "RUNNING".equals(p.getStatus()))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public PreviewStatusResponse getPreviewStatus(String projectId) {
        return repository.findByProjectId(projectId)
                .map(instance -> PreviewStatusResponse.builder()
                        .projectId(projectId)
                        .status(instance.getStatus())
                        .url(("RUNNING".equals(instance.getStatus()) ||
                                "REBUILDING".equals(instance.getStatus()))
                                ? previewUrl(projectId) : null)
                        .updatedAt(instance.getUpdatedAt())
                        .build())
                .orElse(PreviewStatusResponse.builder()
                        .projectId(projectId)
                        .status("NOT_FOUND")
                        .build());
    }

    private void safeCleanup(String projectId, String containerId, String containerName) {
        try {
            if (containerId != null && !containerId.isBlank()) {
                dockerService.removeContainer(projectId, containerId);
            } else if (containerName != null) {
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