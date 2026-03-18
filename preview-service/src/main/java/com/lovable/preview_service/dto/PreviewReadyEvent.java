package com.lovable.preview_service.dto;

import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PreviewReadyEvent {
    private String projectId;
    private String url;
}