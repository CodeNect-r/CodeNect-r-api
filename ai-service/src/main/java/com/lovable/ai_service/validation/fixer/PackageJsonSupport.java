package com.lovable.ai_service.validation.fixer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lovable.ai_service.dto.GeneratedFile;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class PackageJsonSupport {

    private final ObjectMapper mapper = new ObjectMapper();

    public GeneratedFile fixPackageJson(List<GeneratedFile> files, String framework) {
        GeneratedFile pkg = files.stream()
                .filter(f -> f.getPath().equals("package.json"))
                .findFirst().orElse(null);
        try {
            ObjectNode root = (pkg == null || pkg.getContent() == null || pkg.getContent().isBlank())
                    ? buildDefaultPackageJson()
                    : (ObjectNode) mapper.readTree(pkg.getContent());

            if (!root.has("name")) root.put("name", "app");
            if (!root.has("version")) root.put("version", "0.0.0");
            if (!root.has("private")) root.put("private", true);

            ObjectNode scripts = ensureObj(root, "scripts");
            ObjectNode deps = ensureObj(root, "dependencies");
            ObjectNode devDeps = ensureObj(root, "devDependencies");

            switch (framework) {
                case "react-vite" -> {
                    root.put("type", "module");
                    putIfMissing(scripts, "dev", "vite");
                    putIfMissing(scripts, "build", "vite build");
                    putIfMissing(scripts, "preview", "vite preview");
                    putIfMissing(deps, "react", "^18.2.0");
                    putIfMissing(deps, "react-dom", "^18.2.0");
                    putIfMissing(deps, "react-router-dom", "^6.22.0");
                    putIfMissing(deps, "lucide-react", "^0.395.0");
                    putIfMissing(deps, "framer-motion", "^11.2.0");
                    putIfMissing(deps, "clsx", "^2.1.1");
                    putIfMissing(deps, "axios", "^1.7.2");
                    putIfMissing(deps, "prop-types", "^15.8.1");
                    putIfMissing(devDeps, "vite", "^5.0.0");
                    putIfMissing(devDeps, "@vitejs/plugin-react", "^4.2.0");
                    putIfMissing(devDeps, "tailwindcss", "^4.0.0");
                    putIfMissing(devDeps, "@tailwindcss/vite", "^4.0.0");
                }
                case "next" -> {
                    putIfMissing(scripts, "dev", "next dev");
                    putIfMissing(scripts, "build", "next build");
                    putIfMissing(scripts, "start", "next start");
                    putIfMissing(deps, "next", "^14.2.0");
                    putIfMissing(deps, "react", "^18.2.0");
                    putIfMissing(deps, "react-dom", "^18.2.0");
                    putIfMissing(deps, "lucide-react", "^0.395.0");
                    putIfMissing(deps, "framer-motion", "^11.2.0");
                    putIfMissing(deps, "clsx", "^2.1.1");
                    putIfMissing(deps, "axios", "^1.7.2");
                    putIfMissing(deps, "prop-types", "^15.8.1");
                    putIfMissing(devDeps, "tailwindcss", "^3.4.0");
                    putIfMissing(devDeps, "postcss", "^8.4.0");
                    putIfMissing(devDeps, "autoprefixer", "^10.4.0");
                }
                case "react-cra" -> {
                    putIfMissing(scripts, "start", "react-scripts start");
                    putIfMissing(scripts, "build", "react-scripts build");
                    putIfMissing(deps, "react", "^18.2.0");
                    putIfMissing(deps, "react-dom", "^18.2.0");
                    putIfMissing(deps, "react-scripts", "5.0.1");
                    putIfMissing(deps, "lucide-react", "^0.395.0");
                    putIfMissing(deps, "axios", "^1.7.2");
                    putIfMissing(deps, "prop-types", "^15.8.1");
                    putIfMissing(devDeps, "tailwindcss", "^3.4.0");
                    putIfMissing(devDeps, "autoprefixer", "^10.4.0");
                }
                case "vue-vite" -> {
                    root.put("type", "module");
                    putIfMissing(scripts, "dev", "vite");
                    putIfMissing(scripts, "build", "vite build");
                    putIfMissing(deps, "vue", "^3.4.0");
                    putIfMissing(deps, "vue-router", "^4.3.0");
                    putIfMissing(deps, "pinia", "^2.1.7");
                    putIfMissing(deps, "axios", "^1.7.2");
                    putIfMissing(deps, "lucide-vue-next", "^0.395.0");
                    putIfMissing(devDeps, "vite", "^5.0.0");
                    putIfMissing(devDeps, "@vitejs/plugin-vue", "^5.0.4");
                    putIfMissing(devDeps, "tailwindcss", "^4.0.0");
                    putIfMissing(devDeps, "@tailwindcss/vite", "^4.0.0");
                }
                case "angular" -> {
                    putIfMissing(scripts, "start", "ng serve");
                    putIfMissing(scripts, "build", "ng build");
                    putIfMissing(deps, "@angular/core", "^17.0.0");
                    putIfMissing(deps, "@angular/common", "^17.0.0");
                    putIfMissing(deps, "@angular/router", "^17.0.0");
                    putIfMissing(deps, "@angular/forms", "^17.0.0");
                    putIfMissing(deps, "@angular/platform-browser", "^17.0.0");
                    putIfMissing(deps, "@angular/platform-browser-dynamic", "^17.0.0");
                    putIfMissing(deps, "@angular/compiler", "^17.0.0");
                    putIfMissing(deps, "rxjs", "^7.8.0");
                    putIfMissing(deps, "tslib", "^2.6.0");
                    putIfMissing(deps, "zone.js", "^0.14.0");
                    putIfMissing(devDeps, "@angular-devkit/build-angular", "^17.0.0");
                    putIfMissing(devDeps, "@angular/cli", "^17.0.0");
                    putIfMissing(devDeps, "@angular/compiler-cli", "^17.0.0");
                    putIfMissing(devDeps, "typescript", "^5.2.0");
                    putIfMissing(devDeps, "tailwindcss", "^3.4.0");
                    putIfMissing(devDeps, "postcss", "^8.4.0");
                    putIfMissing(devDeps, "autoprefixer", "^10.4.0");
                }
            }

            return GeneratedFile.builder()
                    .path("package.json")
                    .content(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root))
                    .build();

        } catch (Exception e) {
            return null;
        }
    }

    public GeneratedFile addDepToPackageJson(List<GeneratedFile> files, String framework, String pkgName) {
        try {
            GeneratedFile pkg = files.stream()
                    .filter(f -> f.getPath().equals("package.json"))
                    .findFirst().orElse(null);
            if (pkg == null) return fixPackageJson(files, framework);

            ObjectNode root = (ObjectNode) mapper.readTree(pkg.getContent());
            ensureObj(root, "dependencies").put(pkgName, knownVersion(pkgName));

            return GeneratedFile.builder()
                    .path("package.json")
                    .content(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root))
                    .build();
        } catch (Exception e) {
            return fixPackageJson(files, framework);
        }
    }

    private ObjectNode buildDefaultPackageJson() {
        ObjectNode root = mapper.createObjectNode();
        root.put("name", "app");
        root.put("version", "0.0.0");
        root.put("private", true);
        root.set("scripts", mapper.createObjectNode());
        root.set("dependencies", mapper.createObjectNode());
        root.set("devDependencies", mapper.createObjectNode());
        return root;
    }

    private ObjectNode ensureObj(ObjectNode parent, String key) {
        if (parent.has(key) && parent.get(key).isObject()) return (ObjectNode) parent.get(key);
        ObjectNode node = mapper.createObjectNode();
        parent.set(key, node);
        return node;
    }

    private void putIfMissing(ObjectNode node, String key, String value) {
        if (!node.has(key)) node.put(key, value);
    }

    private String knownVersion(String pkg) {
        return switch (pkg) {
            case "react", "react-dom" -> "^18.2.0";
            case "react-router-dom" -> "^6.22.0";
            case "lucide-react" -> "^0.395.0";
            case "framer-motion" -> "^11.2.0";
            case "clsx" -> "^2.1.1";
            case "axios" -> "^1.7.2";
            case "prop-types" -> "^15.8.1";
            case "uuid" -> "^9.0.0";
            case "date-fns" -> "^3.6.0";
            case "zustand" -> "^4.5.2";
            case "lodash" -> "^4.17.21";
            case "recharts" -> "^2.12.0";
            case "react-hot-toast" -> "^2.4.1";
            case "react-hook-form" -> "^7.51.0";
            case "zod" -> "^3.23.0";
            case "@tanstack/react-query" -> "^5.40.0";
            case "@mui/material" -> "^5.15.0";
            case "antd" -> "^5.17.0";
            case "react-icons" -> "^5.2.0";
            case "tailwindcss" -> "^4.0.0";
            case "@tailwindcss/vite" -> "^4.0.0";
            case "postcss" -> "^8.4.0";
            case "autoprefixer" -> "^10.4.0";
            default -> "latest";
        };
    }
    public String getVersion(String dep) {

        return switch (dep) {
            case "react" -> "^18.2.0";
            case "react-dom" -> "^18.2.0";
            case "react-router-dom" -> "^6.22.3";
            case "axios" -> "^1.7.2";

            case "vite" -> "^5.0.0";
            case "@vitejs/plugin-react" -> "^4.2.0";

            case "tailwindcss" -> "^4.0.0";
            case "postcss" -> "^8.4.38";
            case "autoprefixer" -> "^10.4.19";

            case "lucide-react" -> "^0.378.0";

            default -> "^latest";
        };
    }
    public Map<String, Object> parse(GeneratedFile file) {
        try {
            if (file == null || file.getContent() == null || file.getContent().isBlank()) {
                return new HashMap<>();
            }

            return mapper.readValue(file.getContent(), Map.class);

        } catch (Exception e) {
            throw new RuntimeException("Failed to parse package.json", e);
        }
    }
    public GeneratedFile toFile(Map<String, Object> json) {
        try {
            String content = mapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(json);

            return GeneratedFile.builder()
                    .path("package.json")
                    .content(content)
                    .build();

        } catch (Exception e) {
            throw new RuntimeException("Failed to build package.json", e);
        }
    }
}