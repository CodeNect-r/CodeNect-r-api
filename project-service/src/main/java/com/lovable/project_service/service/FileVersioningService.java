package com.lovable.project_service.service;

import com.lovable.project_service.entity.FileVersion;
import com.lovable.project_service.entity.ProjectFile;
import com.lovable.project_service.repository.FileVersionRepository;
import com.lovable.project_service.repository.ProjectFileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class FileVersioningService {

    private final ProjectFileRepository projectFileRepository;
    private final FileVersionRepository fileVersionRepository;

    @Transactional
    public void saveOrUpdateFile(
            String projectId,
            String filePath,
            String newContent
    ) {

        ProjectFile projectFile =
                projectFileRepository
                        .findByProjectIdAndFilePath(projectId, filePath)
                        .orElse(null);

        if (projectFile == null) {
            // First time file creation
            projectFile = new ProjectFile();
            projectFile.setProjectId(projectId);
            projectFile.setFilePath(filePath);
            projectFile.setCurrentVersion(1);
            projectFile.setContent(newContent);
            projectFile.setUpdatedAt(LocalDateTime.now());

            projectFileRepository.save(projectFile);

            saveVersion(projectId, filePath, 1, newContent);

        } else {

            int newVersion = projectFile.getCurrentVersion() + 1;

            projectFile.setCurrentVersion(newVersion);
            projectFile.setContent(newContent);
            projectFile.setUpdatedAt(LocalDateTime.now());

            projectFileRepository.save(projectFile);

            saveVersion(projectId, filePath, newVersion, newContent);
        }
    }

    private void saveVersion(
            String projectId,
            String filePath,
            int version,
            String content
    ) {

        FileVersion fileVersion = new FileVersion();
        fileVersion.setProjectId(projectId);
        fileVersion.setFilePath(filePath);
        fileVersion.setVersionNumber(version);
        fileVersion.setContent(content);
        fileVersion.setCreatedAt(LocalDateTime.now());

        fileVersionRepository.save(fileVersion);
    }
}