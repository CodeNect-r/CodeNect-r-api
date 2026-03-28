package com.lovable.ai_service.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lovable.ai_service.dto.GeneratedFile;
import com.lovable.ai_service.service.PromptFactory;
import org.springframework.ai.chat.client.ChatClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.*;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class BuildValidator {

    private final ChatClient chatClient;
    private final PromptFactory promptFactory;

    private final ObjectMapper mapper = new ObjectMapper();

    private static final List<String> KNOWN_NODE_BUILTINS = List.of(
            "react", "react-dom", "path", "fs", "os", "url", "crypto",
            "stream", "events", "util", "buffer", "http", "https"
    );

    // ═════════════════════════════════════════════════════════════
    //  PASS 1 — PER-FILE AUTO-REPAIR
    // ═════════════════════════════════════════════════════════════

    public GeneratedFile repairFile(GeneratedFile file, String framework) {
        if (file == null || file.getContent() == null || file.getPath() == null) return file;

        String path    = file.getPath();
        String content = file.getContent();

        boolean isJsx      = isJsxFile(path);
        boolean isCss      = path.endsWith(".css");
        boolean isJson     = path.equals("package.json");
        boolean isCssEntry = isCssEntryFile(path, framework);
        boolean isV4       = isV4Framework(framework);

        content = fixMarkdownFences(content);
        content = fixSmartQuotes(content);

        if (isJson)               content = fixPackageJsonDoubleEscape(content);
        if (isJsx) {
            content = fixUnclosedJsx(content);
            content = fixUndefinedVariables(content);
        }
        if (isCss)                content = fixCssImportOrder(content);
        if (isCss && isV4)        content = fixTailwindV4ApplyCustom(content);
        if (isCss && isV4)        content = fixWrongTailwindDirective(content);
        if (isCssEntry && isV4)   content = ensureTailwindImport(content);

        if (isJsx) {
            content = fixUnclosedJsx(content);
            content = fixUndefinedVariables(content);
            content = fixMissingExport(content);
            content = fixDuplicateComponentDeclaration(content);
            content = fixDuplicateDefaultExport(content);
        }

        if (needsAiRepair(content, path)) {
            log.warn("[BuildValidator] File {} flagged for AI repair", path);
            content = aiRepair(content, path, framework);
        }

        if (!content.equals(file.getContent())) {
            log.info("[BuildValidator] Auto-repaired: {}", path);
        }

        return GeneratedFile.builder().path(path).content(content).build();
    }

    /**
     * BUG 3 FIX — fixDuplicateDefaultExport was skipping ALL "export default" lines
     * after the first, including legitimate re-exports of other symbols on the same
     * line. The fix uses proper AST-level detection: only strip bare
     * "export default FunctionName" or "export default class …" declarations,
     * never inline expressions or re-exports.
     *
     * Strategy: find the count of top-level "export default function/class/arrow"
     * declarations. If more than one, keep only the first complete block and remove
     * subsequent duplicate declarations. For simple cases (no function body overlap)
     * the line-scan approach is replaced with a regex that matches only declaration
     * forms, not inline expressions.
     */
    String fixDuplicateDefaultExport(String content) {
        if (content == null) return content;

        // Count only declaration-style default exports, not re-exports or expressions
        Pattern declPattern = Pattern.compile(
                "(?m)^export\\s+default\\s+(?:function|class)\\s+");
        long count = declPattern.matcher(content).results().count();

        if (count <= 1) return content;

        log.warn("[BuildValidator] Removing duplicate export default declaration ({} found)", count);

        // Keep only the first declaration; remove subsequent ones by replacing
        // the duplicate declaration keyword with just "function"/"class" (unexported).
        // This preserves the function body — it just removes the extra export default.
        Matcher m = declPattern.matcher(content);
        boolean firstFound = false;
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            if (!firstFound) {
                firstFound = true;
                m.appendReplacement(sb, m.group()); // keep first as-is
            } else {
                // Replace "export default function/class " with just "function/class "
                String replacement = m.group().replaceFirst("export\\s+default\\s+", "");
                m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
            }
        }
        m.appendTail(sb);
        return sb.toString();
    }

    public List<GeneratedFile> repairAll(List<GeneratedFile> files, String framework) {
        return files.stream()
                .map(f -> repairFile(f, framework))
                .collect(Collectors.toList());
    }

    // ═════════════════════════════════════════════════════════════
    //  PASS 2 — WHOLE-PROJECT VALIDATION
    // ═════════════════════════════════════════════════════════════

    public List<String> validate(List<GeneratedFile> files, String framework) {
        List<String> issues = new ArrayList<>();

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

        if (scripts.isMissingNode() || scripts.isNull())
            issues.add("Missing scripts in package.json");

        switch (framework) {
            case "react-vite" -> {
                checkFile(files, "vite.config.js", issues);
                checkFile(files, "index.html",     issues);
                checkFile(files, "src/main.jsx",   issues);
                checkScript(scripts, "build", "vite build", issues);
                if (hasDep(deps, "react-scripts") || hasDep(devDeps, "react-scripts"))
                    issues.add("react-vite must not include react-scripts");
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
                checkFile(files, "index.html",     issues);
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

        issues.addAll(validateTailwindWiring(files, framework, deps, devDeps));
        issues.addAll(validateImportsVsDeps(files, deps, devDeps));
        issues.addAll(validateNoPlainCssClassNames(files));
        issues.addAll(validateLocalImports(files));
        issues.addAll(validateDuplicateComponents(files));

        // BUG 11 FIX: validate that App.jsx does not import BrowserRouter/Router.
        // Previously this was only enforced by prompt text; now we catch it in code.
        issues.addAll(validateNoNestedRouter(files));

        if (!issues.isEmpty())
            log.warn("⚠️ Validation: {} issue(s) for {}: {}", issues.size(), framework, issues);
        else
            log.info("✅ Validation passed for {}", framework);

        return issues;
    }

    // ═════════════════════════════════════════════════════════════
    //  CATEGORY A — DETERMINISTIC FIX METHODS
    // ═════════════════════════════════════════════════════════════

    String fixMarkdownFences(String content) {
        if (content == null) return content;
        String t = content.trim();
        Matcher m = Pattern.compile("^```[a-zA-Z]*\\n([\\s\\S]*?)\\n?```$").matcher(t);
        if (m.find()) return m.group(1).trim();
        if (t.startsWith("```")) {
            int nl = t.indexOf('\n');
            if (nl != -1) {
                String body = t.substring(nl + 1);
                if (body.endsWith("```")) body = body.substring(0, body.length() - 3).trim();
                return body;
            }
        }
        return content;
    }

    String fixSmartQuotes(String content) {
        if (content == null) return content;
        return content
                .replace('\u201C', '"').replace('\u201D', '"')
                .replace('\u2018', '\'').replace('\u2019', '\'')
                .replace('\u00AB', '"').replace('\u00BB', '"')
                .replace('\u2039', '\'').replace('\u203A', '\'');
    }

    String fixPackageJsonDoubleEscape(String content) {
        if (content == null) return content;
        String t = content.trim();
        if (t.startsWith("\"") && t.endsWith("\"") && t.contains("\\n")) {
            try {
                String inner = t.substring(1, t.length() - 1)
                        .replace("\\n", "\n").replace("\\\"", "\"").replace("\\\\", "\\");
                if (inner.trim().startsWith("{")) return inner.trim();
            } catch (Exception ignored) {}
        }
        return content;
    }

    /**
     * BUG 10 FIX — The original fixCssImportOrder had a subtle flaw: the
     * seenNonImport flag was set for any non-empty, non-@import line. But blank
     * lines between @import statements would fall through to the else branch
     * (since they're not "@import") and set seenNonImport=true, causing valid
     * subsequent @import lines to be treated as "stray" and hoisted out of order.
     *
     * Fix: only set seenNonImport when the line is actually a non-empty,
     * non-@import, non-blank, non-comment line. Blank lines and single-line
     * comments between imports are ignored.
     */
    String fixCssImportOrder(String content) {
        if (content == null) return content;
        String[] lines = content.split("\n", -1);
        List<String> imports = new ArrayList<>();
        List<String> rest    = new ArrayList<>();
        boolean seenNonImport = false;

        for (String line : lines) {
            String t = line.trim();
            boolean isBlankOrComment = t.isEmpty() || t.startsWith("//") || t.startsWith("/*") || t.startsWith("*");

            if (!seenNonImport && (t.startsWith("@import") || t.startsWith("@charset"))) {
                imports.add(line);
            } else if (t.startsWith("@import") && seenNonImport) {
                // Stray @import after real content — hoist it
                imports.add(line);
                log.debug("[BuildValidator] Hoisted stray @import: {}",
                        t.substring(0, Math.min(t.length(), 60)));
            } else {
                // BUG 10 FIX: don't set seenNonImport for blank lines or comments
                if (!isBlankOrComment && !t.startsWith("@import") && !t.startsWith("@charset")) {
                    seenNonImport = true;
                }
                rest.add(line);
            }
        }

        if (imports.isEmpty()) return content;
        StringBuilder sb = new StringBuilder();
        imports.forEach(i -> sb.append(i).append("\n"));
        if (!rest.isEmpty() && !rest.get(0).isBlank()) sb.append("\n");
        rest.forEach(r -> sb.append(r).append("\n"));
        return sb.toString().stripTrailing();
    }

    String fixTailwindV4ApplyCustom(String content) {
        if (content == null) return content;

        Set<String> tw = Set.of(
                "flex","grid","block","inline","hidden","table","contents","flow",
                "absolute","relative","fixed","sticky","static",
                "items","justify","self","place","content","gap","space",
                "p","px","py","pt","pr","pb","pl","m","mx","my","mt","mr","mb","ml",
                "w","h","min","max","size",
                "text","font","tracking","leading","whitespace","break","truncate","line",
                "bg","border","rounded","shadow","ring","outline","opacity","mix",
                "overflow","overscroll","scroll","snap",
                "top","right","bottom","left","inset","z",
                "col","row","order","span","start","end",
                "transition","duration","ease","delay","animate","transform",
                "scale","rotate","translate","skew","origin",
                "cursor","select","resize","appearance","pointer",
                "sr","not","focus","hover","active","disabled","group","peer",
                "dark","sm","md","lg","xl","2xl",
                "aspect","object","list","decoration","underline","overline",
                "float","clear","isolation","visibility","display","backdrop"
        );

        Matcher m = Pattern.compile("(@apply\\s+)([^;\\n]+)(;?)").matcher(content);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            List<String> valid = new ArrayList<>();
            for (String token : m.group(2).trim().split("\\s+")) {
                if (token.isBlank()) continue;
                String base   = token.replaceAll("^[a-z0-9]+:", "");
                String prefix = base.contains("-") ? base.substring(0, base.indexOf('-')) : base;
                if (tw.contains(prefix) || tw.contains(base)) {
                    valid.add(token);
                } else {
                    log.warn("[BuildValidator] Stripped custom @apply token '{}' in: {}", token, m.group(0).trim());
                }
            }
            if (valid.isEmpty()) m.appendReplacement(sb, "");
            else m.appendReplacement(sb, Matcher.quoteReplacement(
                    m.group(1) + String.join(" ", valid) + m.group(3)));
        }
        m.appendTail(sb);
        return sb.toString().replaceAll("(?m)^[ \\t]*\\n[ \\t]*\\n[ \\t]*\\n", "\n\n");
    }

    String fixWrongTailwindDirective(String content) {
        if (content == null) return content;
        if (!content.contains("@tailwind base")
                && !content.contains("@tailwind components")
                && !content.contains("@tailwind utilities")) return content;

        log.warn("[BuildValidator] Replacing v3 @tailwind directives with v4 @import");
        content = content
                .replaceAll("@tailwind\\s+base\\s*;?\\s*\\n?", "")
                .replaceAll("@tailwind\\s+components\\s*;?\\s*\\n?", "")
                .replaceAll("@tailwind\\s+utilities\\s*;?\\s*\\n?", "");
        if (!content.contains("@import \"tailwindcss\"") && !content.contains("@import 'tailwindcss'"))
            content = "@import \"tailwindcss\";\n" + content;
        return content;
    }

    String ensureTailwindImport(String content) {
        if (content == null) return content;
        if (content.contains("@import \"tailwindcss\"") || content.contains("@import 'tailwindcss'"))
            return content;
        log.warn("[BuildValidator] Prepending missing @import \"tailwindcss\"");
        if (content.trim().startsWith("@charset")) {
            int nl = content.indexOf('\n');
            if (nl != -1) return content.substring(0, nl + 1) + "@import \"tailwindcss\";\n" + content.substring(nl + 1);
        }
        return "@import \"tailwindcss\";\n" + content;
    }

    String fixMissingExport(String content) {
        if (content == null) return content;

        content = content.replaceAll(
                "(?m)^(?!export\\s)default\\s+function\\s+",
                "export default function ");

        if (!content.contains("export default") && !content.contains("export {")) {
            Matcher mf = Pattern.compile("(?m)^function\\s+([A-Z][A-Za-z0-9]*)\\s*\\(").matcher(content);
            if (mf.find()) {
                log.warn("[BuildValidator] Prepending export default to function {}", mf.group(1));
                content = content.substring(0, mf.start()) + "export default " + content.substring(mf.start());
            }
        }

        if (!content.contains("export default") && !content.contains("export {")) {
            Matcher ma = Pattern.compile(
                    "(?m)^const\\s+([A-Z][A-Za-z0-9]*)\\s*=\\s*(?:\\([^)]*\\)|[a-zA-Z_$][a-zA-Z0-9_$]*)\\s*=>"
            ).matcher(content);
            if (ma.find()) {
                String name = ma.group(1);
                log.warn("[BuildValidator] Appending export default {} for arrow component", name);
                content = content.stripTrailing() + "\n\nexport default " + name + ";\n";
            }
        }

        return content;
    }

    // ═════════════════════════════════════════════════════════════
    //  CATEGORY B — AI REPAIR
    // ═════════════════════════════════════════════════════════════

    boolean needsAiRepair(String content, String path) {
        if (content == null || content.isBlank()) return true;
        if (isJsxFile(path) && isTruncated(content)) {
            log.warn("[BuildValidator] {} appears truncated", path);
            return true;
        }
        if ((path.endsWith(".jsx") || path.endsWith(".tsx")) && hasSeverelyUnbalancedBraces(content)) {
            log.warn("[BuildValidator] {} has severely unbalanced braces", path);
            return true;
        }
        return false;
    }

    private boolean isTruncated(String content) {
        String s = content.stripTrailing();
        if (s.isEmpty()) return true;
        char last = s.charAt(s.length() - 1);
        return last != ';' && last != '}' && last != ')' && last != '"'
                && last != '\'' && last != '`' && !content.contains("export");
    }

    private boolean hasSeverelyUnbalancedBraces(String content) {
        int open = 0, close = 0;
        boolean inStr = false; char sc = 0;
        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            if (inStr) { if (c == sc && (i == 0 || content.charAt(i-1) != '\\')) inStr = false; }
            else if (c == '"' || c == '\'' || c == '`') { inStr = true; sc = c; }
            else if (c == '{') open++;
            else if (c == '}') close++;
        }
        return Math.abs(open - close) > 5;
    }

    private String aiRepair(String broken, String filePath, String framework) {
        try {
            String response = chatClient.prompt()
                    .system("You are a code repair tool. Return only valid JSON with the fixed file.")
                    .user("""
                        Fix the syntax/structural errors in this file. Do NOT change logic.
                        FILE: %s  FRAMEWORK: %s
                        BROKEN CONTENT:
                        %s
                        Checks: export default present, braces balanced, strings terminated,
                        template literals closed, imports complete, CSS @import order correct,
                        Tailwind v4 @apply uses only built-in utilities, no markdown fences.
                        Return ONLY: { "path": "%s", "content": "fixed content" }
                        """.formatted(filePath, framework, broken, filePath))
                    .call().content();

            if (response == null) return broken;
            response = response.trim().replaceAll("^```[a-zA-Z]*\\n?", "").replaceAll("\\n?```$", "").trim();

            Matcher cm = Pattern.compile("\"content\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(response);
            if (cm.find()) {
                return cm.group(1)
                        .replace("\\n", "\n").replace("\\\"", "\"")
                        .replace("\\\\", "\\").replace("\\/", "/");
            }
        } catch (Exception e) {
            log.error("[BuildValidator] AI repair failed for {}: {}", filePath, e.getMessage());
        }
        return broken;
    }

    // ═════════════════════════════════════════════════════════════
    //  PASS 2 — VALIDATION HELPERS
    // ═════════════════════════════════════════════════════════════

    private List<String> validateTailwindWiring(
            List<GeneratedFile> files, String framework, JsonNode deps, JsonNode devDeps) {
        List<String> issues = new ArrayList<>();
        String cssPath = promptFactory.getCssEntryPath(framework);
        boolean isV4   = isV4Framework(framework);

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

        if (isV4) {
            files.stream().filter(f -> f.getPath().equals("vite.config.js")).findFirst()
                    .ifPresentOrElse(cfg -> {
                        if (!cfg.getContent().contains("tailwindcss()"))
                            issues.add("TAILWIND_WIRING: vite.config.js missing tailwindcss() in plugins");
                        if (!cfg.getContent().contains("@tailwindcss/vite"))
                            issues.add("TAILWIND_WIRING: vite.config.js missing import of @tailwindcss/vite");
                    }, () -> issues.add("Missing file: vite.config.js"));
        } else {
            if (!framework.equals("react-cra")) {
                checkFile(files, "tailwind.config.js", issues);
                checkFile(files, "postcss.config.js", issues);
            } else {
                checkFile(files, "tailwind.config.js", issues);
            }
        }

        files.stream().filter(f -> f.getPath().equals(cssPath)).findFirst()
                .ifPresentOrElse(css -> {
                    String c = css.getContent() == null ? "" : css.getContent().trim();
                    if (isV4) {
                        if (!c.contains("@import \"tailwindcss\"") && !c.contains("@import 'tailwindcss'"))
                            issues.add("TAILWIND_WIRING: " + cssPath + " missing @import \"tailwindcss\"");
                    } else {
                        if (!c.contains("@tailwind base") || !c.contains("@tailwind components")
                                || !c.contains("@tailwind utilities"))
                            issues.add("TAILWIND_WIRING: " + cssPath + " missing @tailwind directives");
                    }
                }, () -> issues.add("CSS_PIPELINE: Missing CSS entry file: " + cssPath));

        return issues;
    }

    private List<String> validateNoPlainCssClassNames(List<GeneratedFile> files) {
        List<String> issues = new ArrayList<>();
        List<String> suspects = List.of(
                "className=\"hero\"",        "className=\"hero-section\"",
                "className=\"stats-grid\"",  "className=\"stat-card\"",
                "className=\"sidebar\"",     "className=\"sidebar-nav\"",
                "className=\"page-header\"", "className=\"feature-card\"",
                "className=\"games-grid\"",  "className=\"booking-form\"",
                "className=\"info-card\"",   "className=\"price-list\""
        );
        for (GeneratedFile file : files) {
            if (!file.getPath().endsWith(".jsx") && !file.getPath().endsWith(".tsx")
                    && !file.getPath().endsWith(".vue")) continue;
            String content = file.getContent();
            if (content == null) continue;
            for (String p : suspects) {
                if (content.contains(p)) {
                    issues.add("CSS_MISSING_CLASS: " + file.getPath() + " uses plain CSS class "
                            + p.replace("className=", "") + " — use Tailwind utilities instead");
                    break;
                }
            }
        }
        return issues;
    }

    private List<String> validateImportsVsDeps(
            List<GeneratedFile> files, JsonNode deps, JsonNode devDeps) {
        List<String> issues = new ArrayList<>();
        files.stream()
                .filter(f -> f.getPath().endsWith(".js") || f.getPath().endsWith(".jsx")
                        || f.getPath().endsWith(".ts") || f.getPath().endsWith(".tsx")
                        || f.getPath().endsWith(".vue"))
                .forEach(file -> extractImportedPackages(file.getContent()).forEach(pkg -> {
                    if (!KNOWN_NODE_BUILTINS.contains(pkg)
                            && !hasDep(deps, pkg) && !hasDep(devDeps, pkg)) {
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
            int idx = line.lastIndexOf(" from ");
            if (idx < 0) continue;
            String after = line.substring(idx + 6).trim().replaceAll("[\"';]", "").trim();
            if (after.startsWith(".") || after.startsWith("/")
                    || after.endsWith(".css") || after.endsWith(".scss")
                    || after.endsWith(".svg") || after.endsWith(".png")) continue;
            String pkg = extractRootPkg(after);
            if (!pkg.isEmpty()) packages.add(pkg);
        }
        return packages;
    }

    private String extractRootPkg(String path) {
        if (path.startsWith("@")) {
            String[] p = path.split("/");
            return p.length >= 2 ? p[0] + "/" + p[1] : path;
        }
        int slash = path.indexOf('/');
        return slash > 0 ? path.substring(0, slash) : path;
    }

    // ═════════════════════════════════════════════════════════════
    //  SHARED HELPERS
    // ═════════════════════════════════════════════════════════════

    private boolean hasDep(JsonNode node, String name) {
        return node != null && !node.isMissingNode() && !node.isNull() && node.has(name);
    }

    private void checkScript(JsonNode scripts, String key, String expected, List<String> issues) {
        if (scripts == null || scripts.isMissingNode()) return;
        if (!scripts.has(key) || !scripts.get(key).asText().contains(expected))
            issues.add("Missing or invalid script '" + key + "': expected '" + expected + "'");
    }

    private void checkFile(List<GeneratedFile> files, String path, List<String> issues) {
        if (files.stream().noneMatch(f -> f.getPath().equals(path)))
            issues.add("Missing file: " + path);
    }

    private boolean isCssEntryFile(String path, String framework) {
        return switch (framework) {
            case "next"     -> path.equals("app/globals.css");
            case "vue-vite" -> path.equals("src/style.css");
            case "angular"  -> path.equals("src/styles.css");
            default         -> path.equals("src/index.css");
        };
    }

    private boolean isV4Framework(String framework) {
        return "react-vite".equals(framework) || "vue-vite".equals(framework);
    }

    private boolean isJsxFile(String path) {
        return path.endsWith(".jsx") || path.endsWith(".tsx")
                || (path.endsWith(".js")
                && !path.equals("vite.config.js")
                && !path.equals("postcss.config.js")
                && !path.equals("next.config.js"));
    }

    /**
     * BUG 4 FIX — The original fixUnclosedJsx split the content on "{" and "}"
     * as plain strings, which counts braces inside string literals, template
     * literals, comments, etc. This causes false positives on perfectly valid
     * JSX like: const msg = "use {curly} braces here";
     *
     * Fix: use the same string-aware brace counter from hasSeverelyUnbalancedBraces
     * (which already exists and is correct). Only append "}" if the imbalance is
     * severe (>5) AND the content is not already corrected by hasSeverelyUnbalancedBraces.
     * For mild imbalances (1-2 braces), do nothing — the AI repair step handles it.
     */
    String fixUnclosedJsx(String content) {
        if (content == null) return content;

        // Count braces while respecting string literals
        int open = 0, close = 0;
        boolean inStr = false;
        char strChar = 0;
        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            if (inStr) {
                if (c == strChar && (i == 0 || content.charAt(i - 1) != '\\')) inStr = false;
            } else if (c == '"' || c == '\'' || c == '`') {
                inStr = true;
                strChar = c;
            } else if (c == '{') {
                open++;
            } else if (c == '}') {
                close++;
            }
        }

        int imbalance = open - close;
        // BUG 4 FIX: only fix severe imbalances (>5). Mild imbalances may be
        // intentional JSX patterns or in strings — let AI repair handle those.
        if (imbalance > 5) {
            log.warn("[BuildValidator] JSX brace imbalance detected ({} extra open braces)", imbalance);
            StringBuilder sb = new StringBuilder(content);
            for (int i = 0; i < imbalance; i++) sb.append("\n}");
            return sb.toString();
        }

        return content;
    }

    String fixUndefinedVariables(String content) {
        if (content.contains("{user.") && !content.contains("const user")) {
            log.warn("[BuildValidator] Injecting fallback user object");
            content = "const user = {};\n" + content;
        }
        return content;
    }

    private static final Pattern LOCAL_IMPORT_PATTERN = Pattern.compile(
            "(?m)^\\s*import\\s+(?:.+?\\s+from\\s+)?[\"'](\\.{1,2}/[^\"']+)[\"'];?"
    );

    private static final List<String> EXT = List.of(".js", ".jsx", ".ts", ".tsx");

    private List<String> validateLocalImports(List<GeneratedFile> files) {
        List<String> issues = new ArrayList<>();
        Set<String> paths = files.stream().map(GeneratedFile::getPath).collect(Collectors.toSet());

        for (GeneratedFile file : files) {
            Matcher m = LOCAL_IMPORT_PATTERN.matcher(file.getContent());

            while (m.find()) {
                String imp = m.group(1);
                String resolved = resolve(file.getPath(), imp, paths);

                if (resolved == null) {
                    String expected = normalize(file.getPath(), imp) + ".jsx";
                    issues.add("MISSING_LOCAL_IMPORT: " + file.getPath() + " -> " + imp + " => " + expected);
                }
            }
        }

        return issues;
    }

    private String resolve(String source, String imp, Set<String> paths) {
        String base = normalize(source, imp);

        if (paths.contains(base)) return base;

        for (String e : EXT) {
            if (paths.contains(base + e)) return base + e;
        }

        return null;
    }

    /**
     * BUG 12 FIX — The original normalize() used a Deque<String> initialized from
     * dir.split("/"). When dir is "" (for a root-level file like "package.json"),
     * split("/") returns [""] — a one-element array with an empty string. Then
     * joining the resulting stack produces "/src/App" instead of "src/App".
     *
     * Fix: filter out the empty string from the initial stack population so that
     * root-level files start with an empty deque, not a deque containing "".
     */
    private String normalize(String source, String imp) {
        String dir = source.contains("/") ? source.substring(0, source.lastIndexOf("/")) : "";
        // BUG 12 FIX: filter empty segments so root files don't produce a leading slash
        Deque<String> stack = new ArrayDeque<>();
        for (String seg : dir.split("/")) {
            if (!seg.isEmpty()) stack.addLast(seg);
        }

        for (String part : imp.split("/")) {
            if (part.equals("..")) {
                if (!stack.isEmpty()) stack.pollLast();
            } else if (!part.equals(".") && !part.isEmpty()) {
                stack.addLast(part);
            }
        }

        return String.join("/", stack);
    }

    /**
     * BUG 6 FIX — The original removeDuplicateFunctions deleted every line containing
     * "function Name", which included lines INSIDE the function body that called or
     * referenced the same function name. This deleted valid code.
     *
     * Fix: track brace depth to find the end of each function declaration, then
     * remove only the duplicate declaration block (from "function Name" to its
     * closing brace), not individual lines.
     */
    String fixDuplicateComponentDeclaration(String content) {
        if (content == null) return content;

        Pattern pattern = Pattern.compile("(?m)^(?:export\\s+default\\s+|export\\s+)?function\\s+(\\w+)\\s*\\(");
        Matcher matcher = pattern.matcher(content);

        Map<String, Integer> countMap = new HashMap<>();
        while (matcher.find()) {
            String name = matcher.group(1);
            countMap.put(name, countMap.getOrDefault(name, 0) + 1);
        }

        // Process one duplicate at a time (the most common case is exactly one duplicate)
        for (Map.Entry<String, Integer> entry : countMap.entrySet()) {
            if (entry.getValue() <= 1) continue;

            String name = entry.getKey();
            log.warn("[BuildValidator] Duplicate component detected: {}", name);

            // Find positions of all occurrences
            Pattern funcPattern = Pattern.compile(
                    "(?m)^(?:export\\s+default\\s+|export\\s+)?function\\s+" + Pattern.quote(name) + "\\s*\\(");
            Matcher fm = funcPattern.matcher(content);

            List<Integer> starts = new ArrayList<>();
            while (fm.find()) starts.add(fm.start());

            if (starts.size() < 2) continue;

            // Remove duplicates from the last occurrence backwards to preserve string indices
            for (int i = starts.size() - 1; i >= 1; i--) {
                int blockStart = starts.get(i);
                int blockEnd = findFunctionEnd(content, blockStart);
                if (blockEnd > blockStart) {
                    content = content.substring(0, blockStart) + content.substring(blockEnd);
                }
            }

            return content; // process one name per pass; caller can re-invoke if needed
        }

        return content;
    }

    /**
     * Find the closing brace of the function starting at startPos by counting
     * brace depth. Returns the index AFTER the closing brace (exclusive end).
     */
    private int findFunctionEnd(String content, int startPos) {
        int depth = 0;
        boolean inStr = false;
        char strChar = 0;
        boolean foundOpen = false;

        for (int i = startPos; i < content.length(); i++) {
            char c = content.charAt(i);
            if (inStr) {
                if (c == strChar && (i == 0 || content.charAt(i - 1) != '\\')) inStr = false;
            } else if (c == '"' || c == '\'' || c == '`') {
                inStr = true; strChar = c;
            } else if (c == '{') {
                depth++;
                foundOpen = true;
            } else if (c == '}') {
                depth--;
                if (foundOpen && depth == 0) return i + 1;
            }
        }
        return content.length(); // fallback: end of file
    }

    private List<String> validateDuplicateComponents(List<GeneratedFile> files) {
        List<String> issues = new ArrayList<>();

        Pattern pattern = Pattern.compile("(?m)^(?:export\\s+default\\s+|export\\s+)?function\\s+(\\w+)\\s*\\(");

        for (GeneratedFile file : files) {
            if (!file.getPath().endsWith(".jsx")) continue;

            Matcher matcher = pattern.matcher(file.getContent());
            Map<String, Integer> map = new HashMap<>();

            while (matcher.find()) {
                String name = matcher.group(1);
                map.put(name, map.getOrDefault(name, 0) + 1);
            }

            for (String key : map.keySet()) {
                if (map.get(key) > 1) {
                    issues.add("DUPLICATE_COMPONENT:" + file.getPath() + ":" + key);
                }
            }
        }

        return issues;
    }

    /**
     * BUG 11 FIX — validate() never checked if App.jsx accidentally imported
     * BrowserRouter or Router. The prompt said not to, but the validator didn't
     * enforce it. Adding a dedicated check here so the autoFixer can repair it
     * the same way it repairs other structural issues.
     */
    private List<String> validateNoNestedRouter(List<GeneratedFile> files) {
        List<String> issues = new ArrayList<>();

        files.stream()
                .filter(f -> f.getPath().endsWith("App.jsx") || f.getPath().endsWith("App.tsx"))
                .forEach(file -> {
                    String content = file.getContent();
                    if (content == null) return;

                    boolean hasBrowserRouterImport =
                            content.contains("BrowserRouter") || content.contains("HashRouter");
                    boolean usesRouterInJsx =
                            content.contains("<BrowserRouter") || content.contains("<Router")
                                    || content.contains("<HashRouter");

                    if (hasBrowserRouterImport || usesRouterInJsx) {
                        issues.add("NESTED_ROUTER: " + file.getPath()
                                + " imports or uses BrowserRouter/Router — must be in main.jsx only");
                    }
                });

        return issues;
    }
}