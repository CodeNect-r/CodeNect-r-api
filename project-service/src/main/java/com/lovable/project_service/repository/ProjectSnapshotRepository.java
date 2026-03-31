package com.lovable.project_service.repository;

import com.lovable.project_service.entity.ProjectSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProjectSnapshotRepository extends JpaRepository<ProjectSnapshot, String> {

    Optional<ProjectSnapshot> findFirstByProjectIdAndStatusOrderByCreatedAtDesc(
            String projectId,
            String status
    );

    List<ProjectSnapshot> findByProjectIdOrderByCreatedAtDesc(String projectId);
}