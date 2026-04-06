package com.lovable.ai_service.validation.fixer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lovable.ai_service.prompt.PromptFactory;
import com.lovable.ai_service.service.AiClientService;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public abstract class AbstractFixRule implements FixRule {
    protected final PromptFactory promptFactory;
    protected final AiClientService aiClientService;
    protected final ObjectMapper mapper;
}