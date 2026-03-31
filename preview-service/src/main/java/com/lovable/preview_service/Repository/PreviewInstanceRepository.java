package com.lovable.preview_service.Repository;

import com.lovable.preview_service.entity.PreviewInstance;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface PreviewInstanceRepository
        extends JpaRepository<PreviewInstance,String> {

    Optional<PreviewInstance> findByProjectId(String projectId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PreviewInstance p where p.projectId = :projectId")
    Optional<PreviewInstance> findWithLockByProjectId(String projectId);
}