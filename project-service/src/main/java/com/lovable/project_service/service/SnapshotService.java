package com.lovable.project_service.service;

import com.lovable.project_service.entity.Project;
import com.lovable.project_service.entity.ProjectSnapshot;
import com.lovable.project_service.repository.ProjectRepository;
import com.lovable.project_service.repository.ProjectSnapshotRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class SnapshotService {

    private final ProjectSnapshotRepository snapshotRepository;
    private final ProjectRepository projectRepository;

    @Transactional
    public void createSnapshot(String projectId, String snapshotId, long snapshotTime) {

        // 🔥 mark old snapshot as SUPERSEDED
        snapshotRepository.findFirstByProjectIdAndStatusOrderByCreatedAtDesc(projectId, "ACTIVE")
                .ifPresent(old -> {
                    old.setStatus("SUPERSEDED");
                    snapshotRepository.save(old);
                });

        // 🔥 create new snapshot
        ProjectSnapshot snapshot = ProjectSnapshot.builder()
                .snapshotId(snapshotId)
                .projectId(projectId)
                .snapshotTime(snapshotTime)
                .createdAt(LocalDateTime.now())
                .status("ACTIVE")
                .build();

        snapshotRepository.save(snapshot);

        // 🔥 update project
        Project project = projectRepository.findById(projectId).orElseThrow();
        project.setLatestSnapshotId(snapshotId);
        project.setUpdatedAt(LocalDateTime.now());

        projectRepository.save(project);
    }

    @Transactional
    public void markSnapshotFailed(String snapshotId) {
        snapshotRepository.findById(snapshotId).ifPresent(snapshot -> {
            snapshot.setStatus("FAILED");
            snapshotRepository.save(snapshot);
        });
    }

    public ProjectSnapshot getSnapshot(String projectId, String snapshotId) {
        return snapshotRepository
                .findById(snapshotId)
                .filter(s -> s.getProjectId().equals(projectId))
                .orElseThrow(() -> new RuntimeException("Snapshot not found"));
    }
}