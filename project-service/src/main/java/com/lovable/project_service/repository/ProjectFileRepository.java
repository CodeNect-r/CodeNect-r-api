package com.lovable.project_service.repository;

import com.lovable.project_service.entity.ProjectFile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProjectFileRepository extends JpaRepository<ProjectFile, String> {

    List<ProjectFile> findByProjectId(String projectId);
    Optional<ProjectFile> findByProjectIdAndFilePath(
            String projectId,
            String filePath
    );


    void deleteByProjectId(String projectId);
}