package com.lovable.preview_service.service;

import com.lovable.preview_service.dto.PreviewStatusResponse;
import com.lovable.preview_service.entity.PreviewInstance;
import com.lovable.preview_service.Repository.PreviewInstanceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PreviewOrchestratorService {

    private final PreviewInstanceRepository repository;
    private final PortAllocatorService portAllocator;
    private final DockerService dockerService;
    private final BuildService buildService;
    private final NginxService nginxService;

    @Transactional
    public String startPreview(String projectId) throws Exception {



        Optional<PreviewInstance> existing = repository.findByProjectId(projectId);

        if (existing.isPresent() && "RUNNING".equals(existing.get().getStatus())) {
            return "http://localhost:" + existing.get().getPort();
        }

        int port = portAllocator.allocatePort();

        PreviewInstance instance = PreviewInstance.builder()
                .projectId(projectId)
                .port(port)
                .status("BUILDING")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        repository.save(instance);

        Path buildDir = buildService.prepareBuildDirectory(projectId);

        int maxRetries = 2;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {

                String imageTag =
                        dockerService.buildImage(projectId, buildDir);

                String containerId =
                        dockerService.runContainer(projectId,imageTag, port);

                waitForHealthCheck(port);

                instance.setImageTag(imageTag);
                instance.setContainerId(containerId);
                instance.setStatus("RUNNING");
                instance.setUpdatedAt(LocalDateTime.now());

                repository.save(instance);

                String domain = projectId + ".localhost";

                nginxService.createDomainRouting(domain, port);

                return "http://" + domain;
            } catch (Exception e) {

                if (attempt == maxRetries) {
                    instance.setStatus("FAILED");
                    repository.save(instance);
                    throw e;
                }
            }
        }

        throw new RuntimeException("Preview failed");
    }

    public void stopPreview(String projectId) throws Exception {

        PreviewInstance instance =
                repository.findByProjectId(projectId)
                        .orElseThrow();

        dockerService.stopContainer(projectId,instance.getContainerId());
        dockerService.removeContainer(projectId,instance.getContainerId());

        instance.setStatus("STOPPED");
        instance.setUpdatedAt(LocalDateTime.now());

        repository.save(instance);
    }

    private void waitForHealthCheck(int port)
            throws Exception {

        int retries = 10;

        for (int i = 0; i < retries; i++) {

            try {
                HttpURLConnection connection =
                        (HttpURLConnection)
                                new URL("http://localhost:" + port)
                                        .openConnection();

                connection.setConnectTimeout(2000);
                connection.setReadTimeout(2000);

                int code = connection.getResponseCode();

                if (code >= 200 && code < 500) {
                    return;
                }

            } catch (Exception ignored) {}

            Thread.sleep(2000);
        }

        throw new RuntimeException("Container health check failed");
    }
    public boolean isPreviewRunning(String projectId) {

        return repository.findByProjectId(projectId)
                .map(p -> "RUNNING".equals(p.getStatus()))
                .orElse(false);

    }
    @Transactional
    public void updatePreviewFiles(String projectId) throws Exception {

        PreviewInstance instance =
                repository.findByProjectId(projectId).orElseThrow();

        Path buildDir = buildService.prepareBuildDirectory(projectId);

        dockerService.copyFilesToContainer(
                projectId,
                instance.getContainerId(),
                buildDir
        );

        instance.setUpdatedAt(LocalDateTime.now());
        repository.save(instance);
    }
    public PreviewStatusResponse getPreviewStatus(String projectId) {
        return repository.findByProjectId(projectId)
                .map(instance -> PreviewStatusResponse.builder()
                        .projectId(projectId)
                        .status(instance.getStatus())
                        .port(instance.getPort())
                        .url("RUNNING".equals(instance.getStatus()) ? "http://" + projectId + ".localhost" : null)
                        .updatedAt(instance.getUpdatedAt())
                        .build())
                .orElse(PreviewStatusResponse.builder()
                        .projectId(projectId)
                        .status("NOT_FOUND")
                        .build());
    }
}