package com.lovable.preview_service.service;

import com.lovable.preview_service.dto.ProjectFileResponse;
import com.lovable.preview_service.entity.ProjectType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class BuildService {

    private final WebClient webClient;
    private final ProjectAnalyzer analyzer;
    private final DockerFileFactory dockerfileFactory;

    private String detectPackageManager(List<ProjectFileResponse> files) {

        for (ProjectFileResponse file : files) {

            String path = file.getPath().toLowerCase();

            if (path.equals("yarn.lock"))
                return "yarn";

            if (path.equals("pnpm-lock.yaml"))
                return "pnpm";
        }

        return "npm";
    }

    @Value("${services.project-service.base-url}")
    private String projectServiceBaseUrl;

    public Path prepareBuildDirectory(String projectId, String token) throws IOException {
        List<ProjectFileResponse> files = webClient.get()
                .uri(projectServiceBaseUrl + "/" + projectId + "/files")
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .bodyToFlux(ProjectFileResponse.class)
                .collectList()
                .block();

        if (files == null || files.isEmpty()) {
            throw new RuntimeException("No files found for project: " + projectId);
        }

        Path tempDir = Files.createTempDirectory("preview-" + projectId);
        boolean hasDockerfile = false;

        for (ProjectFileResponse file : files) {
            // Clean the path (e.g., "src/index.html")
            String cleanPath = file.getPath().replaceFirst("^/+", "");
            Path filePath = tempDir.resolve(cleanPath);

            Files.createDirectories(filePath.getParent());
            Files.writeString(filePath, file.getContent());

            if (cleanPath.equalsIgnoreCase("Dockerfile")) {
                hasDockerfile = true;
            }
        }

        // FALLBACK: Create a Frontend-specific Dockerfile using Nginx
        if (!hasDockerfile) {

            ProjectType type = analyzer.detect(files);
            String packageManager = detectPackageManager(files);

            if (type == ProjectType.UNKNOWN) {
                throw new RuntimeException("Unsupported AI generated project type");
            }

            String dockerfile = dockerfileFactory.generate(type,packageManager);

            Files.writeString(tempDir.resolve("Dockerfile"), dockerfile);
        }

        return tempDir;
    }
}