package com.lovable.ai_service.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GeneratedFile {
    private String path;
    private String content;
}