package com.lovable.ai_service.prompt;

import com.lovable.ai_service.dto.GeneratedFile;
import com.lovable.ai_service.dto.GenerationMode;
import com.lovable.ai_service.dto.PromptContext;
import com.lovable.ai_service.dto.UICriticReport;

import java.util.List;

public interface PromptFactory {

    String buildSystemPrompt(GenerationMode mode);

    String buildPlanningSystemPrompt();

    String buildPlanningPrompt(String userPrompt, String framework);

    String buildSingleFilePrompt(PromptContext context, String filePath);

    String buildCssAuditPrompt(List<GeneratedFile> files, String framework, String userPrompt);

    String buildSummarySystemPrompt();

    String buildSummaryPrompt(String userPrompt, String framework, List<GeneratedFile> files, GenerationMode mode);

    String buildUiFixPrompt(PromptContext context, UICriticReport critique, String filePath);

    String detectFramework(String userPrompt);

    List<String> sortFilesForGeneration(List<String> files);

    String getCssEntryPath(String framework);
}