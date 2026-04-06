package com.lovable.ai_service.dependency;

import com.lovable.ai_service.dto.GeneratedFile;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class DependencyExtractorService {

    private static final Pattern IMPORT_PATTERN =
            Pattern.compile("from ['\"]([^'\"]+)['\"]");

    public Map<String, DependencyType> extractDependencies(List<GeneratedFile> files) {

        Map<String, DependencyType> deps = new HashMap<>();

        for (GeneratedFile file : files) {

            if (file.getContent() == null) continue;

            Matcher matcher = IMPORT_PATTERN.matcher(file.getContent());

            while (matcher.find()) {

                String imp = matcher.group(1);

                if (imp.startsWith("./") || imp.startsWith("../")) continue;

                String normalized = normalize(imp);

                DependencyType type = classify(normalized);

                deps.put(normalized, type); // ✅ dedupe automatically
            }
        }

        return deps;
    }
    private String normalize(String imp) {

        // scoped packages
        if (imp.startsWith("@")) {
            String[] parts = imp.split("/");
            return parts.length >= 2 ? parts[0] + "/" + parts[1] : imp;
        }

        // normal packages
        return imp.split("/")[0];
    }
    private DependencyType classify(String dep) {

        return switch (dep) {
            case "vite",
                 "@vitejs/plugin-react",
                 "tailwindcss",
                 "postcss",
                 "autoprefixer" -> DependencyType.DEV_DEPENDENCY;

            default -> DependencyType.DEPENDENCY;
        };
    }
}