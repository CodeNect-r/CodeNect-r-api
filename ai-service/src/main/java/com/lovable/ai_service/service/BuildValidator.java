package com.lovable.ai_service.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lovable.ai_service.dto.GeneratedFile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Component
@Slf4j
public class BuildValidator {

    private final ObjectMapper mapper = new ObjectMapper();

    // FIX #3: inject PromptFactory so we can use extractTailwindClasses()
    // and getCssEntryPath() — avoids duplicating logic
    @Autowired
    private PromptFactory promptFactory;

    private static final List<String> KNOWN_NODE_BUILTINS = List.of(
            "react", "react-dom", "path", "fs", "os", "url", "crypto",
            "stream", "events", "util", "buffer", "http", "https"
    );

    public List<String> validate(List<GeneratedFile> files, String framework) {
        List<String> issues = new ArrayList<>();

        // ── 1. package.json must exist ──────────────────────────────
        GeneratedFile pkg = files.stream()
                .filter(f -> f.getPath().equals("package.json"))
                .findFirst().orElse(null);

        if (pkg == null) {
            issues.add("Missing package.json");
            return issues;
        }

        JsonNode root;
        try {
            root = mapper.readTree(pkg.getContent());
        } catch (Exception e) {
            issues.add("Invalid package.json: cannot parse — " + e.getMessage());
            return issues;
        }

        JsonNode scripts = root.path("scripts");
        JsonNode deps    = root.path("dependencies");
        JsonNode devDeps = root.path("devDependencies");

        if (scripts.isMissingNode() || scripts.isNull()) {
            issues.add("Missing scripts in package.json");
        }

        // ── 2. Framework-specific checks ─────────────────────────────
        switch (framework) {

            case "react-vite" -> {
                checkFile(files, "vite.config.js",  issues);
                checkFile(files, "index.html",       issues);
                checkFile(files, "src/main.jsx",     issues);
                checkScript(scripts, "build", "vite build", issues);
                if (hasDep(deps, "react-scripts") || hasDep(devDeps, "react-scripts")) {
                    issues.add("react-vite must not include react-scripts");
                }
                if (!hasDep(devDeps, "vite"))
                    issues.add("Missing devDependency: vite");
                if (!hasDep(devDeps, "@vitejs/plugin-react"))
                    issues.add("Missing devDependency: @vitejs/plugin-react");
            }

            case "react-cra" -> {
                checkFile(files, "public/index.html", issues);
                checkScript(scripts, "build", "react-scripts build", issues);
                if (!hasDep(deps, "react-scripts"))
                    issues.add("Missing dependency: react-scripts");
            }

            case "next" -> {
                checkFile(files, "app/layout.jsx", issues);
                checkFile(files, "app/page.jsx",   issues);
                checkScript(scripts, "build", "next build", issues);
                if (!hasDep(deps, "next"))
                    issues.add("Missing dependency: next");
            }

            case "vue-vite" -> {
                checkFile(files, "vite.config.js", issues);
                checkFile(files, "index.html",      issues);
                checkScript(scripts, "build", "vite build", issues);
                if (!hasDep(deps, "vue"))
                    issues.add("Missing dependency: vue");
                if (!hasDep(devDeps, "vite"))
                    issues.add("Missing devDependency: vite");
            }

            case "angular" -> {
                checkFile(files, "angular.json",   issues);
                checkFile(files, "tsconfig.json",  issues);
                checkFile(files, "src/main.ts",    issues);
                checkFile(files, "src/index.html", issues);
                checkFile(files, "src/styles.css", issues);
                checkScript(scripts, "build", "ng build", issues);
                if (!hasDep(deps, "@angular/core"))
                    issues.add("Missing dependency: @angular/core");
            }

            default -> issues.add("Unknown framework: " + framework);
        }

        // ── 3. Tailwind wiring validation (FIX #3) ───────────────────
        issues.addAll(validateTailwindWiring(files, framework, deps, devDeps));

        // ── 4. Import vs package.json cross-check ────────────────────
        issues.addAll(validateImportsVsDeps(files, deps, devDeps));

        // ── 5. CSS coverage check (FIX #3) ───────────────────────────
        // Check that any custom className values are not plain CSS names
        // (i.e., the project is actually using Tailwind, not plain CSS)
        issues.addAll(validateNoPlainCssClassNames(files, framework));

        if (!issues.isEmpty()) {
            log.warn("⚠️ Validation: {} issue(s) for {}: {}", issues.size(), framework, issues);
        } else {
            log.info("✅ Validation passed for {}", framework);
        }

        return issues;
    }

    /* =======================================================
       TAILWIND WIRING VALIDATION
    ======================================================= */

    private List<String> validateTailwindWiring(
            List<GeneratedFile> files, String framework,
            JsonNode deps, JsonNode devDeps
    ) {
        List<String> issues = new ArrayList<>();
        String cssPath = promptFactory.getCssEntryPath(framework);
        boolean isV4 = framework.equals("react-vite") || framework.equals("vue-vite");

        // ── POINT 1: Tailwind in package.json ────────────────────────
        if (isV4) {
            if (!hasDep(devDeps, "tailwindcss"))
                issues.add("TAILWIND_WIRING: tailwindcss missing from devDependencies");
            if (!hasDep(devDeps, "@tailwindcss/vite"))
                issues.add("TAILWIND_WIRING: @tailwindcss/vite missing from devDependencies");
        } else {
            if (!hasDep(devDeps, "tailwindcss"))
                issues.add("TAILWIND_WIRING: tailwindcss missing from devDependencies");
            if (!framework.equals("react-cra") && !hasDep(devDeps, "postcss"))
                issues.add("TAILWIND_WIRING: postcss missing from devDependencies");
            if (!hasDep(devDeps, "autoprefixer"))
                issues.add("TAILWIND_WIRING: autoprefixer missing from devDependencies");
        }

        // ── POINT 2: build tool config ────────────────────────────────
        if (isV4) {
            files.stream()
                    .filter(f -> f.getPath().equals("vite.config.js"))
                    .findFirst()
                    .ifPresentOrElse(
                            cfg -> {
                                if (!cfg.getContent().contains("tailwindcss()")) {
                                    issues.add("TAILWIND_WIRING: vite.config.js missing tailwindcss() in plugins");
                                }
                                if (!cfg.getContent().contains("@tailwindcss/vite")) {
                                    issues.add("TAILWIND_WIRING: vite.config.js missing import of @tailwindcss/vite");
                                }
                            },
                            () -> issues.add("Missing file: vite.config.js")
                    );
        } else {
            // v3: needs tailwind.config.js and postcss.config.js
            if (!framework.equals("react-cra")) {
                checkFile(files, "tailwind.config.js", issues);
                checkFile(files, "postcss.config.js",  issues);
            } else {
                checkFile(files, "tailwind.config.js", issues);
                // CRA doesn't need postcss.config.js
            }
        }

        // ── POINT 3: CSS entry file directive ────────────────────────
        files.stream()
                .filter(f -> f.getPath().equals(cssPath))
                .findFirst()
                .ifPresentOrElse(
                        css -> {
                            String content = css.getContent().trim();
                            if (isV4) {
                                if (!content.contains("@import \"tailwindcss\"")
                                        && !content.contains("@import 'tailwindcss'")) {
                                    issues.add("TAILWIND_WIRING: " + cssPath
                                            + " is missing @import \"tailwindcss\"; as first line");
                                }
                            } else {
                                boolean hasAll = content.contains("@tailwind base")
                                        && content.contains("@tailwind components")
                                        && content.contains("@tailwind utilities");
                                if (!hasAll) {
                                    issues.add("TAILWIND_WIRING: " + cssPath
                                            + " is missing @tailwind base/components/utilities directives");
                                }
                            }
                        },
                        () -> issues.add("CSS_PIPELINE: Missing CSS entry file: " + cssPath)
                );

        return issues;
    }

    /* =======================================================
       CSS CLASS NAME VALIDATION (FIX #3)
       Detects if the AI accidentally used plain CSS class names
       instead of Tailwind utility classes.
    ======================================================= */

    private List<String> validateNoPlainCssClassNames(
            List<GeneratedFile> files, String framework
    ) {
        List<String> issues = new ArrayList<>();

        // Plain CSS class names that indicate the AI used custom classes
        // instead of Tailwind — these cause broken UI
        List<String> suspectPatterns = List.of(
                "className=\"hero\"",       "className=\"hero-section\"",
                "className=\"stats-grid\"", "className=\"stat-card\"",
                "className=\"sidebar\"",    "className=\"sidebar-nav\"",
                "className=\"page-header\"","className=\"feature-card\"",
                "className=\"games-grid\"", "className=\"booking-form\"",
                "className=\"info-card\"",  "className=\"price-list\""
        );

        for (GeneratedFile file : files) {
            if (!file.getPath().endsWith(".jsx") && !file.getPath().endsWith(".tsx")
                    && !file.getPath().endsWith(".vue")) continue;

            String content = file.getContent();
            if (content == null) continue;

            for (String pattern : suspectPatterns) {
                if (content.contains(pattern)) {
                    issues.add("CSS_MISSING_CLASS: " + file.getPath()
                            + " uses plain CSS class " + pattern.replace("className=", "")
                            + " — should use Tailwind utility classes instead. "
                            + "This class has no CSS definition and will produce broken UI.");
                    break; // one issue per file is enough
                }
            }
        }

        return issues;
    }

    /* =======================================================
       IMPORT vs PACKAGE.JSON
    ======================================================= */

    private List<String> validateImportsVsDeps(
            List<GeneratedFile> files, JsonNode deps, JsonNode devDeps
    ) {
        List<String> issues = new ArrayList<>();

        files.stream()
                .filter(f -> f.getPath().endsWith(".js")   || f.getPath().endsWith(".jsx")
                        || f.getPath().endsWith(".ts")   || f.getPath().endsWith(".tsx")
                        || f.getPath().endsWith(".vue"))
                .forEach(file -> extractImportedPackages(file.getContent())
                        .forEach(pkg -> {
                            if (!KNOWN_NODE_BUILTINS.contains(pkg)
                                    && !hasDep(deps, pkg)
                                    && !hasDep(devDeps, pkg)) {
                                issues.add("MISSING_DEP: " + file.getPath()
                                        + " imports '" + pkg + "' but it is not in package.json.");
                            }
                        }));

        return issues;
    }

    private List<String> extractImportedPackages(String content) {
        List<String> packages = new ArrayList<>();
        if (content == null) return packages;
        for (String line : content.split("\n")) {
            line = line.trim();
            if (!line.startsWith("import ")) continue;
            int fromIdx = line.lastIndexOf(" from ");
            if (fromIdx < 0) continue;
            String after = line.substring(fromIdx + 6).trim().replaceAll("[\"';]", "").trim();
            if (after.startsWith(".") || after.startsWith("/")
                    || after.endsWith(".css") || after.endsWith(".scss")
                    || after.endsWith(".svg") || after.endsWith(".png")) continue;
            String pkgName = extractRootPkg(after);
            if (!pkgName.isEmpty()) packages.add(pkgName);
        }
        return packages;
    }

    private String extractRootPkg(String path) {
        if (path.startsWith("@")) {
            String[] parts = path.split("/");
            return parts.length >= 2 ? parts[0] + "/" + parts[1] : path;
        }
        int slash = path.indexOf('/');
        return slash > 0 ? path.substring(0, slash) : path;
    }

    /* =======================================================
       HELPERS
    ======================================================= */

    private boolean hasDep(JsonNode node, String name) {
        if (node == null || node.isMissingNode() || node.isNull()) return false;
        return node.has(name);
    }

    private void checkScript(JsonNode scripts, String key, String expected, List<String> issues) {
        if (scripts == null || scripts.isMissingNode()) return;
        if (!scripts.has(key) || !scripts.get(key).asText().contains(expected)) {
            issues.add("Missing or invalid script '" + key + "': expected '" + expected + "'");
        }
    }

    private void checkFile(List<GeneratedFile> files, String path, List<String> issues) {
        if (files.stream().noneMatch(f -> f.getPath().equals(path))) {
            issues.add("Missing file: " + path);
        }
    }
}