package com.lovable.preview_service.Repository;

import com.lovable.preview_service.entity.PreviewLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PreviewLogRepository extends JpaRepository<PreviewLog, UUID> {
    List<PreviewLog> findTop200ByProjectIdOrderByCreatedAtDesc(String projectId);
}