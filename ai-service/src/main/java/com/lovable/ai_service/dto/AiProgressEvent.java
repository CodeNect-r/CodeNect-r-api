package com.lovable.ai_service.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiProgressEvent {
    private String projectId;
    private String sessionId;
    private String filePath;   // null for non-file messages like planning
    private String message;    // "Planning project structure...", "Generating src/main.tsx..."
    private String status;     // PLANNING | GENERATING | COMPLETED | DONE | ERROR
}