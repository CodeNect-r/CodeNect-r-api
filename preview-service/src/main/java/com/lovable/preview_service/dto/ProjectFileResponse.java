package com.lovable.preview_service.dto;


import lombok.Data;

@Data
public class ProjectFileResponse {

    private String path;
    private int currentVersion;
    private String content;
}