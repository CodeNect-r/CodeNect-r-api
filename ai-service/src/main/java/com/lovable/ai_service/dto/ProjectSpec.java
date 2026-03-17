package com.lovable.ai_service.dto;

import lombok.*;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectSpec {
    private String framework;
    private List<String> files;
}