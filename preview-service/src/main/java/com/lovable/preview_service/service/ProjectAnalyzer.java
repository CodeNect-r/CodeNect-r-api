package com.lovable.preview_service.service;

import com.lovable.preview_service.dto.ProjectFileResponse;
import com.lovable.preview_service.entity.ProjectType;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProjectAnalyzer {

    public ProjectType detect(List<ProjectFileResponse> files) {

        boolean hasPackageJson = false;
        boolean hasViteConfig = false;
        boolean hasNextConfig = false;
        boolean hasNextStructure = false;
        boolean hasReact = false;
        boolean hasAngular = false;
        boolean hasVue = false;
        boolean hasIndexHtmlAtRoot = false;

        for (ProjectFileResponse file : files) {

            String path = file.getPath().replaceFirst("^/+", "").toLowerCase();

            // ---- Detect package.json ----
            if (path.equals("package.json")) {
                hasPackageJson = true;

                String content = file.getContent().toLowerCase();

                if (content.contains("\"react\""))
                    hasReact = true;

                if (content.contains("\"next\""))
                    hasNextConfig = true;

                if (content.contains("\"vue\""))
                    hasVue = true;

                if (content.contains("\"@angular\""))
                    hasAngular = true;
            }

            // ---- Detect Vite ----
            if (path.equals("vite.config.js")
                    || path.equals("vite.config.ts")
                    || path.equals("vite.config.mjs")) {
                hasViteConfig = true;
            }

            // ---- Detect Next config ----
            if (path.equals("next.config.js")
                    || path.equals("next.config.mjs")) {
                hasNextConfig = true;
            }

            // ---- Detect Next structure (Next 13+) ----
            if (path.startsWith("app/")
                    || path.startsWith("pages/")) {
                hasNextStructure = true;
            }

            // ---- Detect Angular ----
            if (path.equals("angular.json"))
                hasAngular = true;

            // ---- Detect static root index ----
            if (path.equals("index.html"))
                hasIndexHtmlAtRoot = true;
        }

        // ===== PRIORITY ORDER MATTERS =====

        if (hasNextConfig || hasNextStructure)
            return ProjectType.NEXT;

        if (hasAngular)
            return ProjectType.NODE; // you can create ANGULAR type later

        if (hasViteConfig && hasReact)
            return ProjectType.REACT_VITE;

        if (hasViteConfig && hasVue)
            return ProjectType.VITE;

        if (hasPackageJson && hasReact)
            return ProjectType.REACT_CRA;

        if (hasPackageJson)
            return ProjectType.NODE;

        if (hasIndexHtmlAtRoot)
            return ProjectType.STATIC;

        return ProjectType.UNKNOWN;
    }
}