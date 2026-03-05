package com.lovable.preview_service.service;

import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;

@Service
public class DockerService {

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
                System.out.println(line);  // 🔥 print docker logs
                output.append(line).append("\n");
            }
        }

        int exitCode = process.waitFor();

        if (exitCode != 0) {
            throw new RuntimeException("Docker build failed:\n" + output);
        }

        return tag;
    }
    public String runContainer(String imageTag, int port) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(
                "docker", "run", "-d" ,"--rm",
                "-p", port + ":80",
                "--memory=512m",
                "--cpus=0.5",
                imageTag
        );

        pb.redirectErrorStream(true);
        Process process = pb.start();

        // READ FIRST, THEN WAIT
        // We need to capture the output (Container ID) before the process object is cleaned up
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line);
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            // Read error stream to see why it failed (e.g., Port already in use)
            try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                String error = errorReader.readLine();
                throw new RuntimeException("Docker run failed: " + error);
            }
        }

        String containerId = output.toString().trim();
        if (containerId.isEmpty()) {
            throw new RuntimeException("Docker started but returned no Container ID");
        }

        return containerId;
    }

    public void stopContainer(String containerId) throws Exception {
        new ProcessBuilder("docker", "stop", containerId)
                .start().waitFor();
    }

    public void removeContainer(String containerId) throws Exception {
        new ProcessBuilder("docker", "rm", containerId)
                .start().waitFor();
    }
}