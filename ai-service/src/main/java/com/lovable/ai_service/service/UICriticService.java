package com.lovable.ai_service.service;

import com.lovable.ai_service.dto.GeneratedFile;
import com.lovable.ai_service.dto.PromptContext;
import com.lovable.ai_service.dto.UICriticReport;

import java.util.List;

public interface UICriticService {
    UICriticReport critique(PromptContext context, List<GeneratedFile> files);
}