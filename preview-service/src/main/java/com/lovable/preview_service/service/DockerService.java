package com.lovable.preview_service.service;

import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;

@Service
public class DockerService {
    private final PreviewLogService previewLogService;

    public DockerService(PreviewLogService previewLogService) {
        this.previewLogService = previewLogService;
    }

    public String buildImage(String projectId, Path buildDir) throws Exception {
        String tag = "preview-" + projectId.toLowerCase();

        ProcessBuilder pb = new ProcessBuilder(
                "docker", "build", "-t", tag, buildDir.toAbsolutePath().toString()
        );

        pb.redirectErrorStream(true);
        Process process = pb.start();

        StringBuilder output = new StringBuilder();

        try (BufferedReader reader =
                     new BufferedReader(new InputStreamReader(process.getInputStream()))) {

            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
                output.append(line).append("\n");

                // log every build line
                previewLogService.append(projectId, "[BUILD] " + line);
            }
        }

        int exitCode = process.waitFor();

        if (exitCode != 0) {
            previewLogService.append(projectId, "[BUILD][ERROR] Docker build failed");
            throw new RuntimeException("Docker build failed:\n" + output);
        }

        previewLogService.append(projectId, "[BUILD] Image built successfully: " + tag);
        return tag;
    }

    public String runContainer(String projectId, String imageTag, int port) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(
                "docker", "run", "-d", "--rm",
                "-p", port + ":80",
                "--memory=512m",
                "--cpus=0.5",
                imageTag
        );

        pb.redirectErrorStream(true);
        Process process = pb.start();

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");

                // log docker run output lines
                previewLogService.append(projectId, "[RUN] " + line);
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            String errorMessage = output.toString().trim();
            previewLogService.append(projectId, "[RUN][ERROR] " + errorMessage);
            throw new RuntimeException("Docker run failed: " + errorMessage);
        }

        String containerId = output.toString().trim();
        if (containerId.isEmpty()) {
            previewLogService.append(projectId, "[RUN][ERROR] Docker started but returned no Container ID");
            throw new RuntimeException("Docker started but returned no Container ID");
        }

        previewLogService.append(projectId, "[RUN] Container started successfully: " + containerId);
        return containerId;
    }

    public void stopContainer(String projectId, String containerId) throws Exception {
        previewLogService.append(projectId, "[STOP] Stopping container: " + containerId);

        Process process = new ProcessBuilder("docker", "stop", containerId).start();
        int exitCode = process.waitFor();

        if (exitCode == 0) {
            previewLogService.append(projectId, "[STOP] Container stopped: " + containerId);
        } else {
            previewLogService.append(projectId, "[STOP][ERROR] Failed to stop container: " + containerId);
        }
    }

    public void removeContainer(String projectId, String containerId) throws Exception {
        previewLogService.append(projectId, "[REMOVE] Removing container: " + containerId);

        Process process = new ProcessBuilder("docker", "rm", containerId).start();
        int exitCode = process.waitFor();

        if (exitCode == 0) {
            previewLogService.append(projectId, "[REMOVE] Container removed: " + containerId);
        } else {
            previewLogService.append(projectId, "[REMOVE][ERROR] Failed to remove container: " + containerId);
        }
    }

    public void copyFilesToContainer(String projectId, String containerId, Path projectDir) throws Exception {
        previewLogService.append(projectId, "[COPY] Copying files to container: " + containerId);

        ProcessBuilder pb = new ProcessBuilder(
                "docker", "cp",
                projectDir.toAbsolutePath().toString() + "/.",
                containerId + ":/app"
        );

        Process process = pb.start();
        int exitCode = process.waitFor();

        if (exitCode == 0) {
            previewLogService.append(projectId, "[COPY] Files copied successfully");
        } else {
            previewLogService.append(projectId, "[COPY][ERROR] Failed to copy files to container");
            throw new RuntimeException("Failed to copy files to container");
        }
    }
}