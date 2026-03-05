package com.lovable.preview_service.Repository;

import com.lovable.preview_service.entity.PreviewInstance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PreviewInstanceRepository
        extends JpaRepository<PreviewInstance, String> {

    Optional<PreviewInstance> findByProjectId(String projectId);
}