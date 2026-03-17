package com.lovable.project_service.repository;

import com.lovable.project_service.entity.ProjectCreateRequestRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProjectCreateRequestRecordRepository extends JpaRepository<ProjectCreateRequestRecord, String> {
    Optional<ProjectCreateRequestRecord> findByRequestId(String requestId);
}