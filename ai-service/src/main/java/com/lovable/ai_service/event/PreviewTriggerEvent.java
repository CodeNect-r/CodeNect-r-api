package com.lovable.ai_service.event;

import com.lovable.ai_service.dto.AiRequestEvent;
import com.lovable.ai_service.dto.GeneratedFile;
import com.lovable.ai_service.entity.ChatSession;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class PreviewTriggerEvent {

    private AiRequestEvent request;
    private ChatSession session;
    private List<GeneratedFile> files;
    private String framework;
}