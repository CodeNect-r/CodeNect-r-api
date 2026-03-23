package com.lovable.ai_service.validation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lovable.ai_service.dto.GeneratedFile;
import com.lovable.ai_service.service.PromptFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
public class BuildAutoFixer {

    private final ObjectMapper mapper = new ObjectMapper();

    // FIX #4: inject PromptFactory to access getCssEntryPath()
    @Autowired
    private PromptFactory promptFactory;

    public GeneratedFile fix(
            String issue,
            List<GeneratedFile> files,
            String userPrompt,
            String framework
    ) {
        log.info("🛠 Fixing issue: {}", issue);

        // ── Tailwind wiring issues (FIX #4) ───────────────────────
        if (issue.startsWith("TAILWIND_WIRING:")) {
            return fixTailwindWiring(issue, files, framework);
        }

        // ── CSS pipeline issues ────────────────────────────────────
        if (issue.startsWith("CSS_PIPELINE:")) {
            return fixCssPipeline(issue, files, framework);
        }

        // ── Plain CSS class used instead of Tailwind (FIX #4) ─────
        if (issue.startsWith("CSS_MISSING_CLASS:")) {
            return fixPlainCssClass(issue, files, framework);
        }

        // ── Missing dependency ─────────────────────────────────────
        if (issue.startsWith("MISSING_DEP:")) {
            return fixMissingDependency(issue, files, framework);
        }

        // ── package.json issues ────────────────────────────────────
        if (issue.toLowerCase().contains("package.json")
                || issue.contains("Missing or invalid script")
                || issue.contains("Missing dependency")
                || issue.contains("Missing devDependency")) {
            return fixPackageJson(files, framework);
        }

        // ── Missing individual files ───────────────────────────────
        if (issue.contains("Missing file: index.html")) return fixIndexHtml(framework);
        if (issue.contains("Missing file: vite.config.js") || issue.contains("vite.config"))
            return fixViteConfig(files, framework);
        if (issue.contains("Missing file: src/main.jsx"))  return fixMainJsx();
        if (issue.contains("Missing file: app/layout.jsx"))return fixNextLayout();
        if (issue.contains("Missing file: app/page.jsx"))  return fixNextPage();
        if (issue.contains("Missing file: app/globals.css")) return fixCssEntryFile(files, framework);
        if (issue.contains("Missing file: tailwind.config.js")) return fixTailwindConfig(framework);
        if (issue.contains("Missing file: postcss.config.js")) return fixPostcssConfig(framework);
        if (issue.contains("Missing file: src/index.css")
                || issue.contains("Missing CSS entry file")) return fixCssEntryFile(files, framework);

        log.warn("⚠️ No fixer for: {}", issue);
        return null;
    }

    /* =======================================================
       TAILWIND WIRING FIXER (FIX #4)
    ======================================================= */

    private GeneratedFile fixTailwindWiring(String issue, List<GeneratedFile> files, String framework) {
        // Missing Tailwind directive in CSS entry file
        if (issue.contains("missing @import") || issue.contains("missing @tailwind")) {
            return fixCssEntryFile(files, framework);
        }
        // vite.config.js missing tailwindcss() plugin
        if (issue.contains("vite.config.js")) {
            return fixViteConfig(files, framework);
        }
        // Missing tailwind.config.js
        if (issue.contains("tailwind.config.js")) {
            return fixTailwindConfig(framework);
        }
        // Missing postcss.config.js
        if (issue.contains("postcss.config.js")) {
            return fixPostcssConfig(framework);
        }
        // Missing Tailwind devDeps — fix package.json
        if (issue.contains("tailwindcss missing") || issue.contains("@tailwindcss/vite missing")
                || issue.contains("autoprefixer missing") || issue.contains("postcss missing")) {
            return fixPackageJson(files, framework);
        }
        log.warn("⚠️ No specific Tailwind wiring fixer for: {}", issue);
        return null;
    }

    /* =======================================================
       PLAIN CSS CLASS FIXER (FIX #4)
       When the AI used a plain CSS class name instead of Tailwind
       utility classes, we can't easily fix the JSX automatically.
       Instead we flag it and trigger a CSS entry file regeneration
       to at least ensure Tailwind is loaded — the real fix requires
       the JSX to be regenerated using the CSS audit prompt.
    ======================================================= */

    private GeneratedFile fixPlainCssClass(String issue, List<GeneratedFile> files, String framework) {
        log.warn("⚠️ Plain CSS class detected — CSS entry file will be regenerated. "
                + "For full fix, regenerate the JSX file using buildCssAuditPrompt().");
        // Ensure the CSS entry file at minimum has the Tailwind directive
        return fixCssEntryFile(files, framework);
    }

    /* =======================================================
       CSS PIPELINE FIXER
    ======================================================= */

    private GeneratedFile fixCssPipeline(String issue, List<GeneratedFile> files, String framework) {
        if (issue.contains("missing the required directive") || issue.contains("Missing CSS entry file")) {
            return fixCssEntryFile(files, framework);
        }
        if (issue.contains("vite.config.js does not call tailwindcss()")) {
            return fixViteConfig(files, framework);
        }
        if (issue.contains("postcss.config.js")) return fixPostcssConfig(framework);
        if (issue.contains("tailwind.config.js")) return fixTailwindConfig(framework);
        if (issue.contains("Tailwind utility classes found")) return fixPackageJson(files, framework);
        log.warn("⚠️ No CSS pipeline fixer for: {}", issue);
        return null;
    }

    /* =======================================================
       MISSING DEPENDENCY FIXER
    ======================================================= */

    private GeneratedFile fixMissingDependency(String issue, List<GeneratedFile> files, String framework) {
        String pkgName = null;
        int idx = issue.indexOf("imports '");
        if (idx >= 0) {
            int start = idx + 9;
            int end = issue.indexOf("'", start);
            if (end > start) pkgName = issue.substring(start, end);
        }
        if (pkgName == null) return fixPackageJson(files, framework);
        return addDepToPackageJson(files, framework, pkgName);
    }

    /* =======================================================
       PACKAGE.JSON FIXER — always merges, never wipes
    ======================================================= */

    private GeneratedFile fixPackageJson(List<GeneratedFile> files, String framework) {
        GeneratedFile pkg = files.stream()
                .filter(f -> f.getPath().equals("package.json"))
                .findFirst().orElse(null);
        try {
            ObjectNode root = (pkg == null || pkg.getContent() == null || pkg.getContent().isBlank())
                    ? buildDefaultPackageJson()
                    : (ObjectNode) mapper.readTree(pkg.getContent());

            if (!root.has("name"))    root.put("name", "app");
            if (!root.has("version")) root.put("version", "0.0.0");
            if (!root.has("private")) root.put("private", true);

            ObjectNode scripts = ensureObj(root, "scripts");
            ObjectNode deps    = ensureObj(root, "dependencies");
            ObjectNode devDeps = ensureObj(root, "devDependencies");

            switch (framework) {
                case "react-vite" -> {
                    root.put("type", "module");
                    putIfMissing(scripts, "dev",     "vite");
                    putIfMissing(scripts, "build",   "vite build");
                    putIfMissing(scripts, "preview", "vite preview");
                    putIfMissing(deps, "react",            "^18.2.0");
                    putIfMissing(deps, "react-dom",        "^18.2.0");
                    putIfMissing(deps, "react-router-dom", "^6.22.0");
                    putIfMissing(deps, "lucide-react",     "^0.395.0");
                    putIfMissing(deps, "framer-motion",    "^11.2.0");
                    putIfMissing(deps, "clsx",             "^2.1.1");
                    putIfMissing(deps, "axios",            "^1.7.2");
                    putIfMissing(deps, "prop-types",       "^15.8.1");
                    putIfMissing(devDeps, "vite",                 "^5.0.0");
                    putIfMissing(devDeps, "@vitejs/plugin-react", "^4.2.0");
                    putIfMissing(devDeps, "tailwindcss",          "^4.0.0");
                    putIfMissing(devDeps, "@tailwindcss/vite",    "^4.0.0");
                }
                case "next" -> {
                    putIfMissing(scripts, "dev",   "next dev");
                    putIfMissing(scripts, "build", "next build");
                    putIfMissing(scripts, "start", "next start");
                    putIfMissing(deps, "next",          "^14.2.0");
                    putIfMissing(deps, "react",         "^18.2.0");
                    putIfMissing(deps, "react-dom",     "^18.2.0");
                    putIfMissing(deps, "lucide-react",  "^0.395.0");
                    putIfMissing(deps, "framer-motion", "^11.2.0");
                    putIfMissing(deps, "clsx",          "^2.1.1");
                    putIfMissing(deps, "axios",         "^1.7.2");
                    putIfMissing(deps, "prop-types",    "^15.8.1");
                    putIfMissing(devDeps, "tailwindcss",   "^3.4.0");
                    putIfMissing(devDeps, "postcss",       "^8.4.0");
                    putIfMissing(devDeps, "autoprefixer",  "^10.4.0");
                }
                case "react-cra" -> {
                    putIfMissing(scripts, "start", "react-scripts start");
                    putIfMissing(scripts, "build", "react-scripts build");
                    putIfMissing(deps, "react",         "^18.2.0");
                    putIfMissing(deps, "react-dom",     "^18.2.0");
                    putIfMissing(deps, "react-scripts", "5.0.1");
                    putIfMissing(deps, "lucide-react",  "^0.395.0");
                    putIfMissing(deps, "axios",         "^1.7.2");
                    putIfMissing(deps, "prop-types",    "^15.8.1");
                    putIfMissing(devDeps, "tailwindcss",  "^3.4.0");
                    putIfMissing(devDeps, "autoprefixer", "^10.4.0");
                }
                case "vue-vite" -> {
                    root.put("type", "module");
                    putIfMissing(scripts, "dev",   "vite");
                    putIfMissing(scripts, "build", "vite build");
                    putIfMissing(deps, "vue",             "^3.4.0");
                    putIfMissing(deps, "vue-router",      "^4.3.0");
                    putIfMissing(deps, "pinia",           "^2.1.7");
                    putIfMissing(deps, "axios",           "^1.7.2");
                    putIfMissing(deps, "lucide-vue-next", "^0.395.0");
                    putIfMissing(devDeps, "vite",               "^5.0.0");
                    putIfMissing(devDeps, "@vitejs/plugin-vue", "^5.0.4");
                    putIfMissing(devDeps, "tailwindcss",        "^4.0.0");
                    putIfMissing(devDeps, "@tailwindcss/vite",  "^4.0.0");
                }
                case "angular" -> {
                    putIfMissing(scripts, "start", "ng serve");
                    putIfMissing(scripts, "build", "ng build");
                    putIfMissing(deps, "@angular/core",                    "^17.0.0");
                    putIfMissing(deps, "@angular/common",                  "^17.0.0");
                    putIfMissing(deps, "@angular/router",                  "^17.0.0");
                    putIfMissing(deps, "@angular/forms",                   "^17.0.0");
                    putIfMissing(deps, "@angular/platform-browser",        "^17.0.0");
                    putIfMissing(deps, "@angular/platform-browser-dynamic","^17.0.0");
                    putIfMissing(deps, "@angular/compiler",                "^17.0.0");
                    putIfMissing(deps, "rxjs",    "^7.8.0");
                    putIfMissing(deps, "tslib",   "^2.6.0");
                    putIfMissing(deps, "zone.js", "^0.14.0");
                    putIfMissing(devDeps, "@angular-devkit/build-angular", "^17.0.0");
                    putIfMissing(devDeps, "@angular/cli",                  "^17.0.0");
                    putIfMissing(devDeps, "@angular/compiler-cli",         "^17.0.0");
                    putIfMissing(devDeps, "typescript",                    "^5.2.0");
                    putIfMissing(devDeps, "tailwindcss",  "^3.4.0");
                    putIfMissing(devDeps, "postcss",      "^8.4.0");
                    putIfMissing(devDeps, "autoprefixer", "^10.4.0");
                }
            }

            log.info("✅ Fixed package.json for {}", framework);
            return GeneratedFile.builder()
                    .path("package.json")
                    .content(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root))
                    .build();

        } catch (Exception e) {
            log.error("❌ Failed to fix package.json", e);
            return null;
        }
    }

    private GeneratedFile addDepToPackageJson(List<GeneratedFile> files, String framework, String pkgName) {
        try {
            GeneratedFile pkg = files.stream()
                    .filter(f -> f.getPath().equals("package.json"))
                    .findFirst().orElse(null);
            if (pkg == null) return fixPackageJson(files, framework);
            ObjectNode root = (ObjectNode) mapper.readTree(pkg.getContent());
            ensureObj(root, "dependencies").put(pkgName, knownVersion(pkgName));
            log.info("📦 Added {}", pkgName);
            return GeneratedFile.builder()
                    .path("package.json")
                    .content(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root))
                    .build();
        } catch (Exception e) {
            return fixPackageJson(files, framework);
        }
    }

    /* =======================================================
       CSS ENTRY FILE FIXER
    ======================================================= */

    private GeneratedFile fixCssEntryFile(List<GeneratedFile> files, String framework) {
        boolean isV4 = framework.equals("react-vite") || framework.equals("vue-vite");
        String cssPath = promptFactory.getCssEntryPath(framework);

        String existing = files.stream()
                .filter(f -> f.getPath().equals(cssPath))
                .map(GeneratedFile::getContent)
                .findFirst().orElse("");

        String header;
        if (isV4) {
            header = "@import \"tailwindcss\";\n\n";
            // Remove any v3 directives
            existing = existing
                    .replaceAll("@tailwind base;?\\s*", "")
                    .replaceAll("@tailwind components;?\\s*", "")
                    .replaceAll("@tailwind utilities;?\\s*", "")
                    .replaceAll("@import [\"']tailwindcss[\"'];?\\s*", "");
        } else {
            header = "@tailwind base;\n@tailwind components;\n@tailwind utilities;\n\n";
            existing = existing
                    .replaceAll("@import [\"']tailwindcss[\"'];?\\s*", "")
                    .replaceAll("@tailwind base;?\\s*", "")
                    .replaceAll("@tailwind components;?\\s*", "")
                    .replaceAll("@tailwind utilities;?\\s*", "");
        }

        log.info("✅ Fixed {} (v4={})", cssPath, isV4);
        return GeneratedFile.builder()
                .path(cssPath)
                .content(header + existing.trim())
                .build();
    }

    /* =======================================================
       VITE CONFIG FIXER — always includes Tailwind
    ======================================================= */

    private GeneratedFile fixViteConfig(List<GeneratedFile> files, String framework) {
        boolean isVue = framework.equals("vue-vite");
        String frameworkImport = isVue
                ? "import vue from '@vitejs/plugin-vue';\n"
                : "import react from '@vitejs/plugin-react';\n";
        String frameworkPlugin = isVue ? "vue()" : "react()";

        String content = """
                import { defineConfig } from 'vite';
                %simport tailwindcss from '@tailwindcss/vite';

                export default defineConfig({
                  plugins: [%s, tailwindcss()],
                });
                """.formatted(frameworkImport, frameworkPlugin);

        log.info("✅ Fixed vite.config.js for {}", framework);
        return GeneratedFile.builder().path("vite.config.js").content(content).build();
    }

    /* =======================================================
       TAILWIND CONFIG FIXER
    ======================================================= */

    private GeneratedFile fixTailwindConfig(String framework) {
        String content = switch (framework) {
            case "next" -> """
                /** @type {import('tailwindcss').Config} */
                module.exports = {
                  content: ['./app/**/*.{js,ts,jsx,tsx,mdx}',
                            './pages/**/*.{js,ts,jsx,tsx,mdx}',
                            './components/**/*.{js,ts,jsx,tsx,mdx}'],
                  theme: { extend: {} }, plugins: [],
                };
                """;
            case "react-cra" -> """
                module.exports = {
                  content: ['./src/**/*.{js,jsx,ts,tsx}', './public/index.html'],
                  theme: { extend: {} }, plugins: [],
                };
                """;
            case "angular" -> """
                module.exports = {
                  content: ['./src/**/*.{html,ts}'],
                  theme: { extend: {} }, plugins: [],
                };
                """;
            default -> {
                log.warn("tailwind.config.js not needed for {} (v4)", framework);
                yield null;
            }
        };
        if (content == null) return null;
        log.info("✅ Fixed tailwind.config.js for {}", framework);
        return GeneratedFile.builder().path("tailwind.config.js").content(content).build();
    }

    /* =======================================================
       POSTCSS CONFIG FIXER
    ======================================================= */

    private GeneratedFile fixPostcssConfig(String framework) {
        boolean cjs = framework.equals("next") || framework.equals("react-cra") || framework.equals("angular");
        String content = cjs
                ? "module.exports = { plugins: { tailwindcss: {}, autoprefixer: {} } };\n"
                : "export default { plugins: { tailwindcss: {}, autoprefixer: {} } };\n";
        log.info("✅ Fixed postcss.config.js for {}", framework);
        return GeneratedFile.builder().path("postcss.config.js").content(content).build();
    }

    /* =======================================================
       OTHER FILE FIXERS
    ======================================================= */

    private GeneratedFile fixIndexHtml(String framework) {
        String src = framework.equals("vue-vite") ? "/src/main.js" : "/src/main.jsx";
        String content = """
                <!DOCTYPE html>
                <html lang="en">
                  <head>
                    <meta charset="UTF-8" />
                    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
                    <title>App</title>
                    <link rel="preconnect" href="https://fonts.googleapis.com" />
                    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin />
                    <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&display=swap" rel="stylesheet" />
                  </head>
                  <body>
                    <div id="root"></div>
                    <script type="module" src="%s"></script>
                  </body>
                </html>
                """.formatted(src);
        return GeneratedFile.builder().path("index.html").content(content).build();
    }

    private GeneratedFile fixMainJsx() {
        return GeneratedFile.builder().path("src/main.jsx").content("""
                import React from 'react';
                import ReactDOM from 'react-dom/client';
                import { BrowserRouter } from 'react-router-dom';
                import App from './App';
                import './index.css';
                ReactDOM.createRoot(document.getElementById('root')).render(
                  <React.StrictMode><BrowserRouter><App /></BrowserRouter></React.StrictMode>
                );
                """).build();
    }

    private GeneratedFile fixNextLayout() {
        return GeneratedFile.builder().path("app/layout.jsx").content("""
                import './globals.css';
                export const metadata = { title: 'App', description: 'App' };
                export default function RootLayout({ children }) {
                  return <html lang="en"><body>{children}</body></html>;
                }
                """).build();
    }

    private GeneratedFile fixNextPage() {
        return GeneratedFile.builder().path("app/page.jsx").content(
                "export default function Home() { return <main><h1>Welcome</h1></main>; }").build();
    }

    /* =======================================================
       HELPERS
    ======================================================= */

    private ObjectNode buildDefaultPackageJson() {
        ObjectNode root = mapper.createObjectNode();
        root.put("name", "app"); root.put("version", "0.0.0"); root.put("private", true);
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
            case "react","react-dom" -> "^18.2.0";
            case "react-router-dom" -> "^6.22.0";
            case "lucide-react"     -> "^0.395.0";
            case "framer-motion"    -> "^11.2.0";
            case "clsx"             -> "^2.1.1";
            case "axios"            -> "^1.7.2";
            case "prop-types"       -> "^15.8.1";
            case "uuid"             -> "^9.0.0";
            case "date-fns"         -> "^3.6.0";
            case "zustand"          -> "^4.5.2";
            case "lodash"           -> "^4.17.21";
            case "recharts"         -> "^2.12.0";
            case "react-hot-toast"  -> "^2.4.1";
            case "react-hook-form"  -> "^7.51.0";
            case "zod"              -> "^3.23.0";
            case "@tanstack/react-query" -> "^5.40.0";
            case "@mui/material"    -> "^5.15.0";
            case "antd"             -> "^5.17.0";
            case "react-icons"      -> "^5.2.0";
            case "tailwindcss"      -> "^4.0.0";
            case "@tailwindcss/vite"-> "^4.0.0";
            case "postcss"          -> "^8.4.0";
            case "autoprefixer"     -> "^10.4.0";
            default                 -> "latest";
        };
    }
}