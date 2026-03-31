package com.lovable.preview_service.controller;

import com.lovable.preview_service.dto.PreviewStatusResponse;
import com.lovable.preview_service.entity.PreviewLog;
import com.lovable.preview_service.service.PreviewLogService;
import com.lovable.preview_service.service.PreviewOrchestratorService;
import com.lovable.preview_service.service.PreviewStreamBufferService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/previews")
@RequiredArgsConstructor
public class PreviewController {

    private final PreviewOrchestratorService previewOrchestratorService;
    private final PreviewLogService previewLogService;

    @PostMapping("/{projectId}/start")
    public PreviewStatusResponse start(@PathVariable String projectId) throws Exception {

        previewOrchestratorService.startPreview(projectId);
        return previewOrchestratorService.getPreviewStatus(projectId);
    }

    @PostMapping("/{projectId}/stop")
    public void stop(@PathVariable String projectId) throws Exception {
        previewOrchestratorService.stopPreview(projectId);
    }

    @GetMapping("/{projectId}")
    public PreviewStatusResponse status(@PathVariable String projectId) {
        return previewOrchestratorService.getPreviewStatus(projectId);
    }

    @GetMapping("/{projectId}/logs")
    public List<PreviewLog> logs(@PathVariable String projectId) {
        return previewLogService.latest(projectId);
    }
}