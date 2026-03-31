package com.lovable.preview_service.service;

import com.lovable.preview_service.Repository.PreviewFileManifestRepository;
import com.lovable.preview_service.dto.PreviewUpdateMode;
import com.lovable.preview_service.dto.PreviewUpdatePlan;
import com.lovable.preview_service.dto.ProjectFileResponse;
import com.lovable.preview_service.entity.PreviewFileManifest;
import com.lovable.preview_service.entity.ProjectType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class BuildService {

    private final WebClient webClient;
    private final ProjectAnalyzer analyzer;
    private final DockerFileFactory dockerfileFactory;
    private final PreviewFileManifestRepository manifestRepository;

    @Value("${services.project-service.base-url}")
    private String projectServiceBaseUrl;

    public PreviewUpdatePlan prepareUpdatePlan(String projectId, String snapshotId) throws IOException {
        List<ProjectFileResponse> files = fetchProjectFiles(projectId, snapshotId);

        if (files == null || files.isEmpty()) {
            throw new RuntimeException("No files found for project: " + projectId);
        }

        ProjectType projectType = analyzer.detect(files);
        if (projectType == ProjectType.UNKNOWN) {
            throw new RuntimeException("Unsupported AI generated project type");
        }

        Path workspaceDir = Path.of("/tmp/previews/" + projectId);
        Files.createDirectories(workspaceDir);

        Map<String, ProjectFileResponse> latestByPath = files.stream()
                .collect(Collectors.toMap(
                        f -> normalizePath(f.getPath()),
                        Function.identity(),
                        (a, b) -> b,
                        LinkedHashMap::new
                ));

        Map<String, PreviewFileManifest> existingManifest = manifestRepository.findByProjectId(projectId).stream()
                .collect(Collectors.toMap(
                        PreviewFileManifest::getFilePath,
                        Function.identity(),
                        (a, b) -> b,
                        LinkedHashMap::new
                ));

        List<String> changedPaths = new ArrayList<>();
        List<String> deletedPaths = new ArrayList<>();
        boolean fullRebuildRequired = existingManifest.isEmpty() || !supportsIncremental(projectType);

        for (ProjectFileResponse file : files) {
            String path = normalizePath(file.getPath());
            String newHash = sha256(file.getContent());

            PreviewFileManifest old = existingManifest.get(path);
            if (old == null
                    || old.getCurrentVersion() != file.getCurrentVersion()
                    || !newHash.equals(old.getContentHash())) {

                changedPaths.add(path);

                if (requiresFullRebuild(path, projectType)) {
                    fullRebuildRequired = true;
                }
            }
        }

        for (String oldPath : existingManifest.keySet()) {
            if (!latestByPath.containsKey(oldPath)) {
                deletedPaths.add(oldPath);

                if (requiresFullRebuild(oldPath, projectType)) {
                    fullRebuildRequired = true;
                }
            }
        }

        if (changedPaths.isEmpty() && deletedPaths.isEmpty()) {
            return PreviewUpdatePlan.builder()
                    .mode(PreviewUpdateMode.NO_CHANGES)
                    .projectType(projectType)
                    .workspaceDir(workspaceDir)
                    .changedPaths(List.of())
                    .deletedPaths(List.of())
                    .build();
        }

        if (fullRebuildRequired) {
            rebuildWorkspaceFromScratch(workspaceDir, files, projectType);
            rebuildManifest(projectId, files);

            return PreviewUpdatePlan.builder()
                    .mode(PreviewUpdateMode.FULL_REBUILD)
                    .projectType(projectType)
                    .workspaceDir(workspaceDir)
                    .changedPaths(changedPaths)
                    .deletedPaths(deletedPaths)
                    .build();
        }

        applyIncrementalWorkspaceChanges(projectId, workspaceDir, files, changedPaths, deletedPaths);
        upsertManifest(projectId, files, changedPaths, deletedPaths);

        return PreviewUpdatePlan.builder()
                .mode(PreviewUpdateMode.INCREMENTAL_UPDATE)
                .projectType(projectType)
                .workspaceDir(workspaceDir)
                .changedPaths(changedPaths)
                .deletedPaths(deletedPaths)
                .build();
    }

    private List<ProjectFileResponse> fetchProjectFiles(String projectId, String snapshotId) {
        if (snapshotId != null && !snapshotId.isBlank()) {
            try {
                return webClient.get()
                        .uri(projectServiceBaseUrl + "/{id}/snapshots/{snapshotId}/files", projectId, snapshotId)
                        .retrieve()
                        .bodyToFlux(ProjectFileResponse.class)
                        .collectList()
                        .block();
            } catch (Exception e) {
                log.warn("Snapshot file endpoint unavailable for projectId={} snapshotId={}, falling back to latest files",
                        projectId, snapshotId);
            }
        }

        return webClient.get()
                .uri(projectServiceBaseUrl + "/{id}/files", projectId)
                .retrieve()
                .bodyToFlux(ProjectFileResponse.class)
                .collectList()
                .block();
    }

    private void rebuildWorkspaceFromScratch(Path workspaceDir,
                                             List<ProjectFileResponse> files,
                                             ProjectType projectType) throws IOException {
        if (Files.exists(workspaceDir)) {
            deleteDirectory(workspaceDir);
        }
        Files.createDirectories(workspaceDir);

        boolean hasDockerfile = false;

        for (ProjectFileResponse file : files) {
            String cleanPath = normalizePath(file.getPath());
            Path filePath = workspaceDir.resolve(cleanPath);

            if (filePath.getParent() != null) {
                Files.createDirectories(filePath.getParent());
            }
            Files.writeString(filePath, file.getContent(),
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            if (cleanPath.equalsIgnoreCase("Dockerfile")) {
                hasDockerfile = true;
            }
        }

        if (!hasDockerfile) {
            String dockerfile = dockerfileFactory.generate(projectType, detectPackageManager(files));
            Files.writeString(workspaceDir.resolve("Dockerfile"), dockerfile,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        }
    }

    private void applyIncrementalWorkspaceChanges(String projectId,
                                                  Path workspaceDir,
                                                  List<ProjectFileResponse> files,
                                                  List<String> changedPaths,
                                                  List<String> deletedPaths) throws IOException {
        Map<String, ProjectFileResponse> byPath = files.stream()
                .collect(Collectors.toMap(
                        f -> normalizePath(f.getPath()),
                        Function.identity(),
                        (a, b) -> b
                ));

        for (String path : changedPaths) {
            ProjectFileResponse file = byPath.get(path);
            if (file == null) continue;

            Path filePath = workspaceDir.resolve(path);
            if (filePath.getParent() != null) {
                Files.createDirectories(filePath.getParent());
            }

            Files.writeString(filePath, file.getContent(),
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            log.info("Incremental workspace updated for projectId={} path={}", projectId, path);
        }

        for (String path : deletedPaths) {
            Path filePath = workspaceDir.resolve(path);
            Files.deleteIfExists(filePath);
            cleanupEmptyParents(workspaceDir, filePath.getParent());
            log.info("Incremental workspace deleted for projectId={} path={}", projectId, path);
        }
    }

    private void rebuildManifest(String projectId, List<ProjectFileResponse> files) {
        manifestRepository.deleteByProjectId(projectId);

        List<PreviewFileManifest> manifests = files.stream()
                .map(file -> PreviewFileManifest.builder()
                        .projectId(projectId)
                        .filePath(normalizePath(file.getPath()))
                        .currentVersion(file.getCurrentVersion())
                        .contentHash(sha256(file.getContent()))
                        .updatedAt(LocalDateTime.now())
                        .build())
                .toList();

        manifestRepository.saveAll(manifests);
    }

    private void upsertManifest(String projectId,
                                List<ProjectFileResponse> files,
                                List<String> changedPaths,
                                List<String> deletedPaths) {
        Map<String, ProjectFileResponse> byPath = files.stream()
                .collect(Collectors.toMap(
                        f -> normalizePath(f.getPath()),
                        Function.identity(),
                        (a, b) -> b
                ));

        for (String path : changedPaths) {
            ProjectFileResponse file = byPath.get(path);
            if (file == null) continue;

            PreviewFileManifest manifest = manifestRepository
                    .findByProjectIdAndFilePath(projectId, path)
                    .orElseGet(() -> PreviewFileManifest.builder()
                            .projectId(projectId)
                            .filePath(path)
                            .build());

            manifest.setCurrentVersion(file.getCurrentVersion());
            manifest.setContentHash(sha256(file.getContent()));
            manifest.setUpdatedAt(LocalDateTime.now());

            manifestRepository.save(manifest);
        }

        for (String path : deletedPaths) {
            manifestRepository.findByProjectIdAndFilePath(projectId, path)
                    .ifPresent(manifestRepository::delete);
        }
    }

    private boolean supportsIncremental(ProjectType type) {
        return type == ProjectType.STATIC || type == ProjectType.NODE;
    }

    private boolean requiresFullRebuild(String path, ProjectType projectType) {
        String p = normalizePath(path).toLowerCase();

        if (projectType != ProjectType.STATIC && projectType != ProjectType.NODE) {
            return true;
        }

        return p.equals("package.json")
                || p.equals("package-lock.json")
                || p.equals("yarn.lock")
                || p.equals("pnpm-lock.yaml")
                || p.equals("dockerfile")
                || p.equals(".npmrc")
                || p.startsWith(".env")
                || p.equals("vite.config.js")
                || p.equals("vite.config.ts")
                || p.equals("vite.config.mjs")
                || p.equals("next.config.js")
                || p.equals("next.config.mjs")
                || p.equals("angular.json")
                || p.equals("tsconfig.json")
                || p.equals("tsconfig.app.json")
                || p.equals("tsconfig.node.json");
    }

    private String detectPackageManager(List<ProjectFileResponse> files) {
        for (ProjectFileResponse file : files) {
            String path = normalizePath(file.getPath()).toLowerCase();
            if (path.equals("yarn.lock")) return "yarn";
            if (path.equals("pnpm-lock.yaml")) return "pnpm";
        }
        return "npm";
    }

    private String normalizePath(String path) {
        return path.replaceFirst("^/+", "").replace("\\", "/");
    }

    private String sha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to hash file content", e);
        }
    }

    private void deleteDirectory(Path path) throws IOException {
        if (!Files.exists(path)) return;

        Files.walk(path)
                .sorted(Comparator.reverseOrder())
                .forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
    }

    private void cleanupEmptyParents(Path root, Path current) throws IOException {
        while (current != null && !current.equals(root) && Files.exists(current)) {
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(current)) {
                if (ds.iterator().hasNext()) {
                    return;
                }
            }
            Files.deleteIfExists(current);
            current = current.getParent();
        }
    }
}