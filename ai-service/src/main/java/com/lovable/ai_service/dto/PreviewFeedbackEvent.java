package com.lovable.ai_service.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PreviewFeedbackEvent {

    private String projectId;
    private PreviewFeedback feedback;
}