package com.lovable.ai_service.regeneration;

import com.lovable.ai_service.dto.GeneratedFile;
import com.lovable.ai_service.service.EmbeddingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * ProjectSnapshot — updated to use Spring AI VectorStore via EmbeddingService.
 *
 * CHANGES FROM PREVIOUS VERSION:
 *   - Removed DocumentEmbeddingRepository dependency entirely
 *   - save() now uses EmbeddingService.loadAllFileContentsForSnapshot()
 *   - rollback() uses EmbeddingService.storeFileEmbeddings()
 *   - No more FileContentProjection, no more PSQLException
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProjectSnapshot {

    private final EmbeddingService embeddingService;

    // projectId → (filePath → fullContent)
    private final ConcurrentHashMap<String, Map<String, String>> snapshots = new ConcurrentHashMap<>();

    /**
     * Save a snapshot of all current file contents for a project.
     * Call this BEFORE starting any regeneration.
     */
    public int save(String projectId) {
        try {
            // Use EmbeddingService — no more repository call that read the vector column
            Map<String, String> fileContents =
                    embeddingService.loadAllFileContentsForSnapshot(projectId);

            if (fileContents.isEmpty()) {
                log.debug("[Snapshot] No content found for project {}", projectId);
                return 0;
            }

            snapshots.put(projectId, fileContents);
            log.info("[Snapshot] Saved {} files for project {}", fileContents.size(), projectId);
            return fileContents.size();

        } catch (Exception e) {
            log.error("[Snapshot] Failed to save snapshot for project {}: {}", projectId, e.getMessage());
            return 0;
        }
    }

    public boolean exists(String projectId) {
        return snapshots.containsKey(projectId);
    }

    public List<GeneratedFile> getFiles(String projectId) {
        Map<String, String> snapshot = snapshots.get(projectId);
        if (snapshot == null) return List.of();
        return snapshot.entrySet().stream()
                .map(e -> GeneratedFile.builder().path(e.getKey()).content(e.getValue()).build())
                .collect(Collectors.toList());
    }

    /**
     * Rollback to the saved snapshot.
     * Re-embeds all snapshot files sequentially to restore the VectorStore.
     */
    public List<GeneratedFile> rollback(String projectId) {
        Map<String, String> snapshot = snapshots.get(projectId);
        if (snapshot == null) {
            log.warn("[Snapshot] No snapshot found for project {} — cannot rollback", projectId);
            return List.of();
        }

        log.warn("[Snapshot] Rolling back project {} ({} files)", projectId, snapshot.size());

        List<GeneratedFile> restoredFiles = new ArrayList<>();
        int ok = 0, fail = 0;

        for (Map.Entry<String, String> entry : snapshot.entrySet()) {
            try {
                GeneratedFile file = GeneratedFile.builder()
                        .path(entry.getKey())
                        .content(entry.getValue())
                        .build();
                // Sequential re-embedding — avoids concurrent DELETE+INSERT race
                embeddingService.storeFileEmbeddings(projectId, file);
                restoredFiles.add(file);
                ok++;
            } catch (Exception e) {
                log.error("[Snapshot] Failed to restore {}: {}", entry.getKey(), e.getMessage());
                fail++;
            }
        }

        log.info("[Snapshot] Rollback complete: {} restored, {} failed", ok, fail);
        return restoredFiles;
    }

    public void clear(String projectId) {
        Map<String, String> removed = snapshots.remove(projectId);
        if (removed != null)
            log.debug("[Snapshot] Cleared snapshot for project {} ({} files)", projectId, removed.size());
    }

    public int size(String projectId) {
        Map<String, String> s = snapshots.get(projectId);
        return s == null ? 0 : s.size();
    }
}