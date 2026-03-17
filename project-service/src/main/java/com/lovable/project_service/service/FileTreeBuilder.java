package com.lovable.project_service.service;

import com.lovable.project_service.dto.FileNodeResponse;
import com.lovable.project_service.entity.ProjectFile;

import java.util.*;

public final class FileTreeBuilder {

    private FileTreeBuilder() {}

    public static List<FileNodeResponse> build(List<ProjectFile> files) {
        Node root = new Node("", "", "DIRECTORY");

        for (ProjectFile file : files) {
            String[] parts = file.getFilePath().split("/");
            Node current = root;
            StringBuilder currentPath = new StringBuilder();

            for (int i = 0; i < parts.length; i++) {
                if (currentPath.length() > 0) currentPath.append('/');
                currentPath.append(parts[i]);

                boolean isFile = i == parts.length - 1;
                current.children.putIfAbsent(parts[i], new Node(parts[i], currentPath.toString(), isFile ? "FILE" : "DIRECTORY"));
                current = current.children.get(parts[i]);

                if (isFile) {
                    current.currentVersion = file.getCurrentVersion();
                    current.size = (long) file.getContent().length();
                    current.updatedAt = file.getUpdatedAt();
                }
            }
        }

        return root.children.values().stream().map(Node::toResponse).toList();
    }

    private static class Node {
        String name;
        String path;
        String type;
        Integer currentVersion;
        Long size;
        java.time.LocalDateTime updatedAt;
        Map<String, Node> children = new TreeMap<>();

        Node(String name, String path, String type) {
            this.name = name;
            this.path = path;
            this.type = type;
        }

        FileNodeResponse toResponse() {
            return FileNodeResponse.builder()
                    .name(name)
                    .path(path)
                    .type(type)
                    .currentVersion(currentVersion)
                    .size(size)
                    .updatedAt(updatedAt)
                    .children(children.values().stream().map(Node::toResponse).toList())
                    .build();
        }
    }
}
