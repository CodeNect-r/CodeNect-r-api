package com.lovable.preview_service.service;

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

@Service
@RequiredArgsConstructor
public class PreviewOrchestratorService {

    private final PreviewInstanceRepository repository;
    private final PortAllocatorService portAllocator;
    private final DockerService dockerService;
    private final BuildService buildService;

    @Transactional
    public String startPreview(String projectId,String authHeader) throws Exception {

        String tokenToSend;
        System.out.println("DEBUG: authHeader parameter: " + (authHeader != null ? "PRESENT" : "NULL"));
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            tokenToSend = authHeader.replace("Bearer ", "");
        }
        // 2. Fallback to SecurityContext (for Web calls where header wasn't passed manually)
        else {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            tokenToSend = (auth != null && auth.getCredentials() != null)
                    ? auth.getCredentials().toString()
                    : null;
        }

        repository.findByProjectId(projectId)
                .ifPresent(existing -> {
                    try {
                        stopPreview(projectId);
                    } catch (Exception ignored) {}
                });

        int port = portAllocator.allocatePort();

        PreviewInstance instance = PreviewInstance.builder()
                .projectId(projectId)
                .port(port)
                .status("BUILDING")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        repository.save(instance);

        Path buildDir = buildService.prepareBuildDirectory(projectId,tokenToSend);

        int maxRetries = 2;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {

                String imageTag =
                        dockerService.buildImage(projectId, buildDir);

                String containerId =
                        dockerService.runContainer(imageTag, port);

                waitForHealthCheck(port);

                instance.setImageTag(imageTag);
                instance.setContainerId(containerId);
                instance.setStatus("RUNNING");
                instance.setUpdatedAt(LocalDateTime.now());

                repository.save(instance);

                return "http://localhost:" + port;

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

        dockerService.stopContainer(instance.getContainerId());
        dockerService.removeContainer(instance.getContainerId());

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
}