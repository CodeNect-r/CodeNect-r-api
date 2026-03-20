package com.lovable.preview_service.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
public class DockerService {

    private final PreviewLogService previewLogService;

    @Value("${preview.docker.network:codenect-r-api_lovable-network}")
    private String previewNetwork;

    public DockerService(PreviewLogService previewLogService) {
        this.previewLogService = previewLogService;
    }

    public void ensureNetworkExists() throws Exception {
        ProcessBuilder checkPb = new ProcessBuilder(
                "docker", "network", "inspect", previewNetwork
        );
        checkPb.redirectErrorStream(true);

        Process checkProcess = checkPb.start();
        checkProcess.waitFor();

        if (checkProcess.exitValue() == 0) {
            return;
        }

        ProcessBuilder createPb = new ProcessBuilder(
                "docker", "network", "create", previewNetwork
        );
        createPb.redirectErrorStream(true);

        Process createProcess = createPb.start();
        String output = readProcessOutput(createProcess, null);
        int exitCode = createProcess.waitFor();

        if (exitCode != 0) {
            throw new RuntimeException("Failed to create docker network: " + output);
        }
    }

    public String buildImage(String projectId, Path buildDir) throws Exception {
        String tag = imageTag(projectId);

        ProcessBuilder pb = new ProcessBuilder(
                "docker", "build", "-t", tag, buildDir.toAbsolutePath().toString()
        );
        pb.redirectErrorStream(true);

        Process process = pb.start();
        String output = readProcessOutput(process, line ->
                previewLogService.append(projectId, "[BUILD] " + line)
        );

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            previewLogService.append(projectId, "[BUILD][ERROR] Docker build failed");
            throw new RuntimeException("Docker build failed:\n" + output);
        }

        previewLogService.append(projectId, "[BUILD] Image built successfully: " + tag);
        return tag;
    }

    public String runContainer(String projectId, String imageTag) throws Exception {
        ensureNetworkExists();

        String containerName = containerName(projectId);
        removeContainerIfExists(projectId, containerName);

        ProcessBuilder pb = new ProcessBuilder(
                "docker", "run", "-d",
                "--name", containerName,
                "--network", previewNetwork,
                "--memory=512m",
                "--cpus=0.5",
                imageTag
        );
        pb.redirectErrorStream(true);

        Process process = pb.start();
        String output = readProcessOutput(process, line ->
                previewLogService.append(projectId, "[RUN] " + line)
        );

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            previewLogService.append(projectId, "[RUN][ERROR] " + output.trim());
            throw new RuntimeException("Docker run failed: " + output.trim());
        }

        String containerId = output.trim();
        if (containerId.isBlank()) {
            throw new RuntimeException("Docker started but returned no container id");
        }

        previewLogService.append(projectId, "[RUN] Container started successfully: " + containerId);
        return containerId;
    }

    public void waitForHttpHealthy(String projectId, String containerName) throws Exception {
        int retries = 20;

        for (int i = 0; i < retries; i++) {
            try {
                ProcessBuilder pb = new ProcessBuilder(
                        "docker", "exec", containerName,
                        "sh", "-c",
                        "wget -q --spider http://127.0.0.1:80 || curl -fsS http://127.0.0.1:80 >/dev/null"
                );
                pb.redirectErrorStream(true);

                Process process = pb.start();
                String output = readProcessOutput(process, null);
                int exitCode = process.waitFor();

                if (exitCode == 0) {
                    previewLogService.append(projectId, "[HEALTH] Container is healthy");
                    return;
                }

                previewLogService.append(projectId, "[HEALTH] Waiting... " + output);
            } catch (Exception ignored) {
            }

            Thread.sleep(2000);
        }

        throw new RuntimeException("Container health check failed for: " + containerName);
    }

    public void stopContainer(String projectId, String containerId) throws Exception {
        if (containerId == null || containerId.isBlank()) {
            return;
        }

        ProcessBuilder pb = new ProcessBuilder("docker", "stop", containerId);
        pb.redirectErrorStream(true);

        Process process = pb.start();
        readProcessOutput(process, line ->
                previewLogService.append(projectId, "[STOP] " + line)
        );
        process.waitFor();
    }

    public void removeContainer(String projectId, String containerId) throws Exception {
        if (containerId == null || containerId.isBlank()) {
            return;
        }

        ProcessBuilder pb = new ProcessBuilder("docker", "rm", "-f", containerId);
        pb.redirectErrorStream(true);

        Process process = pb.start();
        readProcessOutput(process, line ->
                previewLogService.append(projectId, "[REMOVE] " + line)
        );
        process.waitFor();
    }

    public void removeContainerIfExists(String projectId, String containerName) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(
                "docker", "ps", "-a",
                "--filter", "name=^/" + containerName + "$",
                "--format", "{{.Names}}"
        );
        pb.redirectErrorStream(true);

        Process process = pb.start();
        String output = readProcessOutput(process, null);
        process.waitFor();

        if (output.trim().equals(containerName)) {
            ProcessBuilder rmPb = new ProcessBuilder("docker", "rm", "-f", containerName);
            rmPb.redirectErrorStream(true);

            Process rmProcess = rmPb.start();
            readProcessOutput(rmProcess, line ->
                    previewLogService.append(projectId, "[CLEANUP] " + line)
            );
            rmProcess.waitFor();
        }
    }

    public void copyFilesToContainer(String projectId, String containerId, Path projectDir) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(
                "docker", "cp",
                projectDir.toAbsolutePath() + "/.",
                containerId + ":/app"
        );
        pb.redirectErrorStream(true);

        Process process = pb.start();
        String output = readProcessOutput(process, line ->
                previewLogService.append(projectId, "[COPY] " + line)
        );

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("Failed to copy files to container: " + output);
        }
    }

    public String containerName(String projectId) {
        return ("preview-" + projectId)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_.-]", "-");
    }

    public String imageTag(String projectId) {
        return ("preview-" + projectId)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_.-]", "-");
    }

    private String readProcessOutput(Process process, java.util.function.Consumer<String> lineConsumer) throws Exception {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            return reader.lines()
                    .peek(line -> {
                        if (lineConsumer != null) {
                            lineConsumer.accept(line);
                        }
                    })
                    .collect(Collectors.joining("\n"));
        }
    }
}