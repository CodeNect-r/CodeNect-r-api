package com.lovable.ai_service.regeneration;

import com.lovable.ai_service.service.EmbeddingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.*;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class ImpactAnalyzer {

    private final EmbeddingService embeddingService;
    private final AdaptiveThresholdStore thresholdStore;
    private final FileRoutingService fileRoutingService;
    private final ArtifactPlanningService artifactPlanningService;
    private final IntentClassificationService intentClassificationService;
    private static final int SEARCH_LIMIT = 10;
    private static final int FALLBACK_N = 2;
    private static final int IMPORT_GRAPH_DEPTH = 2;

    private final ConcurrentHashMap<String, Double> lastSimilarityScores = new ConcurrentHashMap<>();

    public enum Intent { ADD_FILE, MODIFY, FIX_BUG, RESTYLE, REFACTOR }

    public record RequestedArtifact(
            String rawName,
            String normalizedName,
            String type,
            String filePath,
            String componentName
    ) {}

    public record AnalysisResult(
            Intent intent,
            Set<String> impactedFiles,
            List<RequestedArtifact> requestedArtifacts,
            double confidenceScore,
            boolean globalStyleChange,
            StyleScope styleScope
    ) {
        public boolean isAddFile() { return intent == Intent.ADD_FILE; }

        public boolean isRestyle() { return intent == Intent.RESTYLE; }

        public boolean isGlobalStyleChange() {
            return globalStyleChange || styleScope == StyleScope.GLOBAL;
        }

        public String confidenceLabel() {
            if (confidenceScore >= 0.65) return "High confidence";
            if (confidenceScore >= 0.35) return "Medium confidence";
            return "Best guess";
        }
    }

    public AnalysisResult analyze(String projectId, String userPrompt) {
        String framework = embeddingService.detectFramework(projectId);

        IntentClassificationService.IntentResult llmIntent =
                intentClassificationService.classify(userPrompt, framework);

        Intent intent = mapIntent(llmIntent.intent(), userPrompt);

        if (intent == Intent.ADD_FILE || llmIntent.addNewArtifacts()) {
            List<RequestedArtifact> requested = artifactPlanningService.planArtifacts(userPrompt, framework)
                    .stream()
                    .map(p -> buildArtifact(p.name(), p.type(), projectId))
                    .toList();

            if (requested.isEmpty()) {
                return new AnalysisResult(intent, Set.of(), List.of(), 0.0, false, StyleScope.UNKNOWN);
            }

            return new AnalysisResult(intent, Set.of(), requested, llmIntent.confidence(), false, StyleScope.UNKNOWN);
        }

        if (intent == Intent.RESTYLE) {
            StyleScope styleScope = parseStyleScope(llmIntent.styleScope(), userPrompt);
            boolean globalStyleChange = styleScope == StyleScope.GLOBAL;

            Set<String> styleFiles = detectStyleFiles(projectId, userPrompt, globalStyleChange);
            return new AnalysisResult(intent, styleFiles, List.of(), llmIntent.confidence(), globalStyleChange, styleScope);
        }

        double threshold = thresholdStore.computeAndRecord(projectId);
        Set<String> direct = findByEmbeddingSimilarity(projectId, userPrompt, threshold);

        if (direct.isEmpty()) {
            return new AnalysisResult(intent, Set.of(), List.of(), llmIntent.confidence(), false, StyleScope.UNKNOWN);
        }

        Set<String> expanded = expandViaImportGraph(projectId, direct);
        return new AnalysisResult(intent, expanded, List.of(), llmIntent.confidence(), false, StyleScope.UNKNOWN);
    }
    private StyleScope detectStyleScope(String prompt) {
        if (prompt == null || prompt.isBlank()) return StyleScope.UNKNOWN;

        String lower = prompt.toLowerCase();

        if (containsAny(lower,
                "whole app", "entire app", "global", "globally", "sitewide",
                "all pages", "entire website", "whole website", "overall theme",
                "app background", "main background", "background color of app",
                "change theme", "change app theme", "update theme")) {
            return StyleScope.GLOBAL;
        }

        if (containsAny(lower,
                "this page", "this component", "only this", "specific page",
                "hero section", "card", "button", "navbar", "footer", "header")) {
            return StyleScope.LOCAL;
        }

        if (lower.contains("background") || lower.contains("theme") || lower.contains("color")) {
            return StyleScope.GLOBAL;
        }

        return StyleScope.UNKNOWN;
    }

    private boolean containsAny(String text, String... values) {
        for (String value : values) {
            if (text.contains(value)) return true;
        }
        return false;
    }

    private Intent mapIntent(String llmIntent, String userPrompt) {
        if (llmIntent == null) return classifyIntent(userPrompt);

        return switch (llmIntent.trim().toUpperCase()) {
            case "ADD_FILE" -> Intent.ADD_FILE;
            case "FIX_BUG" -> Intent.FIX_BUG;
            case "RESTYLE" -> Intent.RESTYLE;
            case "REFACTOR" -> Intent.REFACTOR;
            case "MODIFY" -> Intent.MODIFY;
            default -> classifyIntent(userPrompt);
        };
    }

    private StyleScope parseStyleScope(String scope, String userPrompt) {
        if (scope == null || scope.isBlank()) return detectStyleScope(userPrompt);

        return switch (scope.trim().toUpperCase()) {
            case "GLOBAL" -> StyleScope.GLOBAL;
            case "LOCAL" -> StyleScope.LOCAL;
            default -> detectStyleScope(userPrompt);
        };
    }
    private static final List<Pattern> ADD_PATTERNS = List.of(
            Pattern.compile("\\b(add|create|make|build|generate|implement|new)\\b.{0,60}\\b(page|component|screen|view|section|modal|dialog|panel|layout|widget|hook|util|helper)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bnew\\s+(page|component|screen|route|file|feature)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(introduce|scaffold|set\\s+up)\\b", Pattern.CASE_INSENSITIVE)
    );

    private static final List<Pattern> RESTYLE_PATTERNS = List.of(
            Pattern.compile("\\b(color|colour|font|theme|dark\\s+mode|light\\s+mode|spacing|padding|margin|background|shadow|gradient|animation)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(restyle|redesign|prettify|beautify|better\\s+design|modern\\s+look|improve\\s+the\\s+ui)\\b", Pattern.CASE_INSENSITIVE)
    );

    private static final List<Pattern> FIX_PATTERNS = List.of(
            Pattern.compile("\\b(fix|broken|bug|error|issue|problem|doesn'?t\\s+work|not\\s+working|fails?|crash|wrong|incorrect)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(can'?t|cannot|won'?t|will\\s+not)\\b", Pattern.CASE_INSENSITIVE)
    );

    private static final List<Pattern> REFACTOR_PATTERNS = List.of(
            Pattern.compile("\\b(refactor|restructure|reorganize|split|extract|separate|modularize|clean\\s+up|simplify)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bsplit.{0,20}into\\b|\\bextract.{0,20}component\\b", Pattern.CASE_INSENSITIVE)
    );

    private Intent classifyIntent(String prompt) {
        if (prompt == null || prompt.isBlank()) return Intent.MODIFY;

        int add = countMatches(prompt, ADD_PATTERNS) * 3;
        int restyle = countMatches(prompt, RESTYLE_PATTERNS);
        int fix = countMatches(prompt, FIX_PATTERNS) * 2;
        int refactor = countMatches(prompt, REFACTOR_PATTERNS) * 2;

        if (add >= 3) return Intent.ADD_FILE;
        if (fix > 0 && fix >= refactor && fix >= restyle) return Intent.FIX_BUG;
        if (refactor > 0 && refactor >= fix && refactor >= restyle) return Intent.REFACTOR;
        if (restyle > 0) return Intent.RESTYLE;
        return Intent.MODIFY;
    }

    private int countMatches(String prompt, List<Pattern> patterns) {
        int n = 0;
        for (Pattern p : patterns) if (p.matcher(prompt).find()) n++;
        return n;
    }




    private RequestedArtifact buildArtifact(String rawName, String type, String projectId) {
        String normalizedName = normalizeArtifactName(rawName);

        String normalizedType = normalizeArtifactType(type);

        String componentName = switch (normalizedType) {
            case "page", "screen", "view" ->
                    normalizedName.endsWith("Page") ? normalizedName : normalizedName + "Page";
            case "hook" ->
                    normalizedName.startsWith("use") ? normalizedName : "use" + normalizedName;
            default -> normalizedName;
        };

        String framework = embeddingService.detectFramework(projectId);
        String filePath = fileRoutingService.resolvePath(framework, normalizedType, componentName);

        return new RequestedArtifact(rawName, normalizedName, normalizedType, filePath, componentName);
    }

    private String normalizeArtifactType(String type) {
        if (type == null || type.isBlank()) return "component";

        String t = type.trim().toLowerCase();

        return switch (t) {
            case "page", "screen", "view",
                 "component", "modal", "dialog",
                 "layout", "hook", "util", "helper",
                 "api", "store" -> t;
            default -> "component";
        };
    }
    private String detectArtifactType(String lower) {
        if (lower.contains("page")) return "page";
        if (lower.contains("screen")) return "screen";
        if (lower.contains("view")) return "view";
        if (lower.contains("modal")) return "modal";
        if (lower.contains("dialog")) return "dialog";
        if (lower.contains("layout")) return "layout";
        if (lower.contains("hook")) return "hook";
        if (lower.contains("util")) return "util";
        if (lower.contains("helper")) return "helper";
        return "component";
    }

    private String normalizeArtifactName(String input) {
        if (input == null || input.isBlank()) {
            return "NewComponent";
        }

        String s = input.trim();

        // split camelCase / PascalCase
        s = s.replaceAll("([a-z])([A-Z])", "$1 $2");

        // split kebab-case / snake_case
        s = s.replaceAll("[-_]+", " ");

        // remove generic suffix words if user included them
        s = s.replaceAll("(?i)\\b(page|pages|component|components|screen|screens|view|views|modal|modals|dialog|dialogs|section|sections|layout|layouts|hook|hooks|util|utils|helper|helpers)\\b", " ");

        // collapse whitespace
        s = s.replaceAll("\\s+", " ").trim();

        if (s.isBlank()) {
            return "NewComponent";
        }

        // if user gave a single glued token like "productdetails" or "addtocart",
        // we keep it as one token and title-case it.
        // if later you want stronger word-splitting, do it with a planner/LLM step,
        // not with hardcoded examples.
        String[] words = s.split("\\s+");

        StringBuilder out = new StringBuilder();
        for (String word : words) {
            if (word.isBlank()) continue;
            out.append(Character.toUpperCase(word.charAt(0)));
            if (word.length() > 1) {
                out.append(word.substring(1));
            }
        }

        String result = out.toString().replaceAll("[^A-Za-z0-9]", "");
        return result.isBlank() ? "NewComponent" : result;
    }
    private String titleWord(String word) {
        String w = word.toLowerCase();
        return switch (w) {
            case "cart" -> "Cart";
            case "product" -> "Product";
            case "details" -> "Details";
            case "checkout" -> "Checkout";
            case "wishlist" -> "Wishlist";
            default -> Character.toUpperCase(w.charAt(0)) + w.substring(1);
        };
    }

    private boolean isGlobalStyleRequest(String prompt) {
        String lower = prompt.toLowerCase();
        return lower.contains("background color")
                || lower.contains("change the background")
                || lower.contains("app background")
                || lower.contains("entire app")
                || lower.contains("whole app")
                || lower.contains("globally")
                || lower.contains("global")
                || lower.contains("theme")
                || lower.contains("all pages")
                || lower.contains("sitewide");
    }

    private Set<String> detectStyleFiles(String projectId, String userPrompt, boolean globalStyleChange) {
        Map<String, String> allContents = embeddingService.loadAllFileContents(projectId);
        if (allContents.isEmpty()) return Set.of();

        List<String> rootCandidates = List.of(
                "src/index.css",
                "src/globals.css",
                "src/global.css",
                "src/styles/global.css",
                "src/styles/index.css",
                "src/App.css",
                "src/app.css"
        );

        LinkedHashSet<String> result = new LinkedHashSet<>();

        if (globalStyleChange) {
            for (String candidate : rootCandidates) {
                if (allContents.containsKey(candidate)) {
                    result.add(candidate);
                }
            }

            if (!result.isEmpty()) {
                if (allContents.containsKey("src/App.jsx")) result.add("src/App.jsx");
                if (allContents.containsKey("src/App.tsx")) result.add("src/App.tsx");
                return result;
            }
        }

        Set<String> styleKeywords = extractStyleKeywords(userPrompt);
        Map<String, Integer> fileScores = new LinkedHashMap<>();

        for (Map.Entry<String, String> entry : allContents.entrySet()) {
            String path = entry.getKey();
            String content = Optional.ofNullable(entry.getValue()).orElse("").toLowerCase();

            int score = 0;

            if (rootCandidates.contains(path)) score += 20;
            if (path.endsWith(".css") || path.endsWith(".scss")) score += 8;
            if (path.contains("/styles/")) score += 6;
            if (path.endsWith("theme.js") || path.endsWith("theme.ts")) score += 6;
            if (content.contains(":root")) score += 5;
            if (content.contains("--")) score += 3;
            if (content.contains("background") || content.contains("bg-")) score += 2;

            for (String kw : styleKeywords) {
                if (content.contains(kw)) score += 1;
            }

            if (score > 0) fileScores.put(path, score);
        }

        return fileScores.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(globalStyleChange ? 4 : 3)
                .map(Map.Entry::getKey)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Set<String> extractStyleKeywords(String prompt) {
        Set<String> keywords = new LinkedHashSet<>();
        String lower = prompt.toLowerCase();

        List.of("red","green","blue","white","black","gray","purple",
                        "yellow","orange","pink","indigo","teal","dark","light")
                .stream().filter(lower::contains).forEach(keywords::add);

        List.of("background","bg-","color","border","shadow","font",
                        "text-","padding","margin","rounded","opacity","theme","global")
                .stream().filter(lower::contains).forEach(keywords::add);

        return keywords;
    }

    private Set<String> findByEmbeddingSimilarity(String projectId, String userPrompt, double threshold) {
        List<EmbeddingService.SimilarFile> similar =
                embeddingService.findSimilarFiles(projectId, userPrompt, SEARCH_LIMIT);

        lastSimilarityScores.clear();
        similar.forEach(s -> {
            if (s.score() != null) lastSimilarityScores.put(s.filePath(), s.score());
        });

        Set<String> matched = similar.stream()
                .filter(s -> s.score() != null && s.score() >= threshold)
                .map(EmbeddingService.SimilarFile::filePath)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (matched.isEmpty() && !similar.isEmpty()) {
            similar.stream()
                    .filter(s -> s.score() != null)
                    .sorted(Comparator.comparingDouble(s -> -s.score()))
                    .limit(FALLBACK_N)
                    .map(EmbeddingService.SimilarFile::filePath)
                    .forEach(matched::add);
        }

        return matched;
    }

    private double computeAverageScore(Set<String> files) {
        if (files.isEmpty()) return 0.0;
        return files.stream()
                .mapToDouble(f -> lastSimilarityScores.getOrDefault(f, 0.0))
                .average().orElse(0.0);
    }

    private Set<String> expandViaImportGraph(String projectId, Set<String> direct) {
        Map<String, String> fileContents = embeddingService.loadAllFileContents(projectId);
        if (fileContents.isEmpty()) return direct;

        Map<String, Set<String>> reverse = buildReverseImportGraph(fileContents);

        Set<String> expanded = new LinkedHashSet<>(direct);
        Queue<String> queue = new LinkedList<>(direct);
        Map<String, Integer> depth = new HashMap<>();
        direct.forEach(f -> depth.put(f, 0));

        while (!queue.isEmpty()) {
            String current = queue.poll();
            int currentDepth = depth.getOrDefault(current, 0);
            if (currentDepth >= IMPORT_GRAPH_DEPTH) continue;

            for (String importer : reverse.getOrDefault(current, Set.of())) {
                if (!expanded.contains(importer)) {
                    expanded.add(importer);
                    depth.put(importer, currentDepth + 1);
                    queue.add(importer);
                }
            }
        }

        return expanded;
    }

    private static final Pattern RELATIVE_IMPORT =
            Pattern.compile("from\\s+['\"](\\.{1,2}/[^'\"\\n]+)['\"]");

    private Map<String, Set<String>> buildReverseImportGraph(Map<String, String> fileContents) {
        Map<String, Set<String>> reverse = new HashMap<>();

        for (Map.Entry<String, String> entry : fileContents.entrySet()) {
            String importer = entry.getKey();
            String content = entry.getValue();
            if (content == null) continue;

            String dir = getDirectory(importer);

            for (String line : content.split("\n")) {
                if (!line.contains("from")) continue;
                Matcher m = RELATIVE_IMPORT.matcher(line);
                if (m.find()) {
                    String resolved = resolvePath(dir, m.group(1));
                    if (resolved != null) {
                        reverse.computeIfAbsent(resolved, k -> new LinkedHashSet<>()).add(importer);
                    }
                }
            }
        }

        return reverse;
    }

    private String resolvePath(String fromDir, String rawImport) {
        try {
            String combined = fromDir.isEmpty() ? rawImport : fromDir + "/" + rawImport;
            Deque<String> stack = new ArrayDeque<>();
            for (String part : combined.split("/")) {
                if (part.equals("..") && !stack.isEmpty()) stack.pollLast();
                else if (!part.equals(".") && !part.isEmpty()) stack.addLast(part);
            }
            String normalized = String.join("/", stack);
            return normalized.matches(".*\\.[a-zA-Z]+$") ? normalized : normalized + ".jsx";
        } catch (Exception e) {
            return null;
        }
    }

    private String getDirectory(String filePath) {
        int i = filePath.lastIndexOf('/');
        return i > 0 ? filePath.substring(0, i) : "";
    }
}