package com.lovable.preview_service.dto;

import lombok.Data;
import java.util.List;

@Data
public class AiResponseEvent {

    private String eventId;
    private String eventVersion;
    private String projectId;
    private String status;
    private List<Object> files;
}