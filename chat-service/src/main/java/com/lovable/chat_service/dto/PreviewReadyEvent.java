package com.lovable.chat_service.dto;

import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PreviewReadyEvent {
    private String projectId;
    private String url;
}