package com.lovable.chat_service.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateProjectEvent {

    private String requestId;

    private String userEmail;

    private String name;

    private String description;

}