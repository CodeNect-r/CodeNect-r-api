package com.lovable.ai_service.dependency;

import com.lovable.ai_service.dto.GeneratedFile;
import com.lovable.ai_service.validation.fixer.PackageJsonSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class PackageJsonEnricherService {

    private final DependencyExtractorService extractor;
    private final PackageJsonSupport packageJsonSupport;

    public List<GeneratedFile> enrich(List<GeneratedFile> files, String framework) {

        // ✅ Step 1: ensure base package.json exists
        GeneratedFile pkgFile = packageJsonSupport.fixPackageJson(files, framework);
        files = replace(files, pkgFile);

        // ✅ Step 2: extract dependencies (DEDUPED + CLASSIFIED)
        Map<String, DependencyType> deps = extractor.extractDependencies(files);

        // ✅ Step 3: parse package.json ONCE
        Map<String, Object> json = packageJsonSupport.parse(pkgFile);

        Map<String, String> dependencies =
                (Map<String, String>) json.computeIfAbsent("dependencies", k -> new HashMap<>());

        Map<String, String> devDependencies =
                (Map<String, String>) json.computeIfAbsent("devDependencies", k -> new HashMap<>());

        // ✅ Step 4: inject dependencies safely
        for (Map.Entry<String, DependencyType> entry : deps.entrySet()) {

            String dep = entry.getKey();

            // 🚫 skip invalid imports
            if (dep.contains("http") || dep.contains("localhost")) continue;

            String version = packageJsonSupport.getVersion(dep);

            if (entry.getValue() == DependencyType.DEV_DEPENDENCY) {
                devDependencies.putIfAbsent(dep, version);
            } else {
                dependencies.putIfAbsent(dep, version);
            }
        }

        // ✅ Step 5: rebuild package.json
        GeneratedFile updatedPkg = packageJsonSupport.toFile(json);

        files = replace(files, updatedPkg);

        return files;
    }

    private List<GeneratedFile> replace(List<GeneratedFile> files, GeneratedFile updated) {

        if (updated == null) return files;

        boolean exists = files.stream()
                .anyMatch(f -> f.getPath().equals(updated.getPath()));

        List<GeneratedFile> result = new ArrayList<>();

        for (GeneratedFile f : files) {
            if (f.getPath().equals(updated.getPath())) {
                result.add(updated);
            } else {
                result.add(f);
            }
        }

        if (!exists) {
            result.add(updated);
        }

        return result;
    }
}