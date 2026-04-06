package com.lovable.ai_service.dto;

import lombok.*;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PreviewFeedback {

    private String projectId;

    private boolean buildSuccess;
    private String buildLogs;

    private boolean runtimeSuccess;
    private String runtimeErrors;

    private boolean healthy;
    private String screenshotBase64;
    private List<String> consoleErrors;
    private List<String> networkErrors;
    private String domSnapshot;
}