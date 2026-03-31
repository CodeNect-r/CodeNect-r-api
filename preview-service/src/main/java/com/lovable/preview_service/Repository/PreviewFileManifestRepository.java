package com.lovable.preview_service.Repository;

import com.lovable.preview_service.entity.PreviewFileManifest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PreviewFileManifestRepository extends JpaRepository<PreviewFileManifest, String> {

    List<PreviewFileManifest> findByProjectId(String projectId);

    Optional<PreviewFileManifest> findByProjectIdAndFilePath(String projectId, String filePath);

    void deleteByProjectId(String projectId);
}