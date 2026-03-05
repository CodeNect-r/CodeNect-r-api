package com.lovable.preview_service.controller;

import com.lovable.preview_service.service.PreviewOrchestratorService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/preview")
@RequiredArgsConstructor
public class PreviewController {

    private final PreviewOrchestratorService orchestrator;

    @PostMapping("/{projectId}/start")
    public String start(@PathVariable String projectId,@RequestHeader("Authorization") String authHeader) throws Exception {
        System.out.println("token:" + authHeader);
        return orchestrator.startPreview(projectId,authHeader);
    }

    @DeleteMapping("/{projectId}")
    public void stop(@PathVariable String projectId) throws Exception {
        orchestrator.stopPreview(projectId);
    }
}