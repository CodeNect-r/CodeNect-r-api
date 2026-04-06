package com.lovable.ai_service.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DesignMemory {

    private String themeStyle;      // glassmorphism / minimal / modern SaaS
    private String colorSystem;     // slate + blue / purple gradient
    private String radius;          // rounded-xl / rounded-2xl
    private String shadow;          // soft / medium / heavy
    private String typography;      // Inter / spacing / scale
}