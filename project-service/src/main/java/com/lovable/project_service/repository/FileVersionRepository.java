package com.lovable.project_service.repository;

import com.lovable.project_service.entity.FileVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FileVersionRepository
        extends JpaRepository<FileVersion, UUID> {

    List<FileVersion> findByProjectIdAndFilePathOrderByVersionNumberDesc(
            String projectId,
            String filePath
    );
}
