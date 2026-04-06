package com.lovable.ai_service.validation;

import com.lovable.ai_service.dto.GeneratedFile;
import lombok.Builder;

import java.util.List;

@Builder
public record FixContext(
        List<GeneratedFile> files,
        String userPrompt,
        String framework
) {}