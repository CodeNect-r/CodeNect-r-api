package com.lovable.preview_service.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class PreviewStreamBufferService {

    private static final long FLUSH_DELAY_MS = 400; // tune: 250-700 ms

    private final PartialUpdateHandler handler;


    private final ScheduledExecutorService scheduler =
            Executors.newScheduledThreadPool(2);

    private final Map<String, BufferedUpdate> buffer = new ConcurrentHashMap<>();

    public void bufferPartialUpdate(
            String projectId,
            String snapshotId,
            String filePath,
            String content
    ) {
        String key = buildKey(projectId, snapshotId, filePath);

        buffer.compute(key, (k, existing) -> {
            if (existing == null) {
                BufferedUpdate created = new BufferedUpdate(projectId, snapshotId, filePath, content);
                created.future = scheduler.schedule(
                        () -> flushKey(k),
                        FLUSH_DELAY_MS,
                        TimeUnit.MILLISECONDS
                );
                return created;
            }

            existing.content = content; // always keep latest content
            if (existing.future != null && !existing.future.isDone()) {
                existing.future.cancel(false);
            }

            existing.future = scheduler.schedule(
                    () -> flushKey(k),
                    FLUSH_DELAY_MS,
                    TimeUnit.MILLISECONDS
            );
            return existing;
        });
    }

    public void flushImmediately(String projectId, String snapshotId, String filePath) {
        flushKey(buildKey(projectId, snapshotId, filePath));
    }

    public void flushAllForProject(String projectId, String snapshotId) {
        buffer.keySet().stream()
                .filter(k -> k.startsWith(projectId + "::" + snapshotId + "::"))
                .toList()
                .forEach(this::flushKey);
    }

    private void flushKey(String key) {
        BufferedUpdate update = buffer.remove(key);
        if (update == null) {
            return;
        }

        try {
            handler.handle(
                    update.projectId,
                    update.snapshotId,
                    update.filePath,
                    update.content
            );
        } catch (Exception e) {
            log.warn("Buffered partial flush failed for project={} file={}",
                    update.projectId, update.filePath, e);
        }
    }

    private String buildKey(String projectId, String snapshotId, String filePath) {
        return projectId + "::" + snapshotId + "::" + filePath;
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdown();
    }

    private static final class BufferedUpdate {
        final String projectId;
        final String snapshotId;
        final String filePath;
        volatile String content;
        volatile ScheduledFuture<?> future;

        BufferedUpdate(String projectId, String snapshotId, String filePath, String content) {
            this.projectId = Objects.requireNonNull(projectId);
            this.snapshotId = Objects.requireNonNull(snapshotId);
            this.filePath = Objects.requireNonNull(filePath);
            this.content = Objects.requireNonNullElse(content, "");
        }
    }
}