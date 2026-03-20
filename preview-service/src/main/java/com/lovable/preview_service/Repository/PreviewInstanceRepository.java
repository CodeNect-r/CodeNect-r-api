package com.lovable.preview_service.Repository;

import com.lovable.preview_service.entity.PreviewInstance;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

public interface PreviewInstanceRepository
        extends JpaRepository<PreviewInstance,String> {

    Optional<PreviewInstance> findByProjectId(String projectId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PreviewInstance> findWithLockByProjectId(String projectId);
}