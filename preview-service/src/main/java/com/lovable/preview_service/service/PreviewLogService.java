package com.lovable.preview_service.service;

import com.lovable.preview_service.entity.PreviewLog;
import com.lovable.preview_service.Repository.PreviewLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PreviewLogService {

    private final PreviewLogRepository repository;

    public void append(String projectId, String line) {
        repository.save(PreviewLog.builder()
                .projectId(projectId)
                .line(line)
                .createdAt(LocalDateTime.now())
                .build());
    }

    public List<PreviewLog> latest(String projectId) {
        return repository.findTop200ByProjectIdOrderByCreatedAtDesc(projectId);
    }
}
