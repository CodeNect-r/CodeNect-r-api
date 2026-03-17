package com.lovable.chat_service.service;

import com.lovable.chat_service.dto.ProjectCreatedEvent;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ProjectCreateReplyHandler {

    private final Map<String, CompletableFuture<ProjectCreatedEvent>> pending = new ConcurrentHashMap<>();

    public CompletableFuture<ProjectCreatedEvent> register(String requestId) {
        CompletableFuture<ProjectCreatedEvent> future = new CompletableFuture<>();
        pending.put(requestId, future);
        return future;
    }

    public void complete(ProjectCreatedEvent event) {
        CompletableFuture<ProjectCreatedEvent> future = pending.remove(event.getRequestId());
        if (future != null) {
            future.complete(event);
        }
    }

    public void fail(String requestId, Throwable throwable) {
        CompletableFuture<ProjectCreatedEvent> future = pending.remove(requestId);
        if (future != null) {
            future.completeExceptionally(throwable);
        }
    }
}