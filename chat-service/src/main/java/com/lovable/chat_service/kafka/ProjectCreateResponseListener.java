package com.lovable.chat_service.kafka;

import com.lovable.chat_service.dto.ProjectCreatedEvent;
import com.lovable.chat_service.service.ProjectCreateReplyHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProjectCreateResponseListener {

    private final ProjectCreateReplyHandler handler;

    @KafkaListener(topics = "project.create.response")
    public void consume(ProjectCreatedEvent event) {

        handler.complete(event);
    }
}