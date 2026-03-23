package com.lovable.ai_service.regeneration;

import com.lovable.ai_service.service.EmbeddingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.*;
import java.util.stream.*;

/**
 * ImpactAnalyzer — updated to use Spring AI VectorStore via EmbeddingService.
 *
 * CHANGES FROM PREVIOUS VERSION:
 *   - Removed DocumentEmbeddingRepository dependency entirely
 *   - findByEmbeddingSimilarity() → EmbeddingService.findSimilarFiles()
 *   - loadAllFileContents() → EmbeddingService.loadAllFileContents()
 *   - detectStyleFiles() → EmbeddingService.loadAllFileContents() (no more FileContentProjection)
 *   - StackOverflowError from RELATIVE_IMPORT regex on full file content is fixed:
 *     content is now processed line-by-line instead of as a single string
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ImpactAnalyzer {

    private final EmbeddingService       embeddingService;
    private final AdaptiveThresholdStore thresholdStore;

    private static final int SEARCH_LIMIT       = 10;
    private static final int FALLBACK_N         = 2;
    private static final int IMPORT_GRAPH_DEPTH = 2;

    // Caches similarity scores from the last search so computeAverageScore()
    // can read them without a second search.
    private final ConcurrentHashMap<String, Double> lastSimilarityScores = new ConcurrentHashMap<>();

    // ═════════════════════════════════════════════════════════════
    //  PUBLIC API
    // ═════════════════════════════════════════════════════════════

    public enum Intent { ADD_FILE, MODIFY, FIX_BUG, RESTYLE, REFACTOR }

    public record AnalysisResult(
            Intent      intent,
            Set<String> impactedFiles,
            String      newFilePath,
            String      newFileComponentName,
            double      confidenceScore
    ) {
        public boolean isAddFile()  { return intent == Intent.ADD_FILE; }
        public boolean isRestyle()  { return intent == Intent.RESTYLE;  }
        public boolean isFixBug()   { return intent == Intent.FIX_BUG;  }
        public boolean isRefactor() { return intent == Intent.REFACTOR; }

        public String confidenceLabel() {
            if (confidenceScore >= 0.65) return "High confidence";
            if (confidenceScore >= 0.35) return "Medium confidence";
            return "Best guess";
        }
    }

    public AnalysisResult analyze(String projectId, String userPrompt) {
        Intent intent = classifyIntent(userPrompt);
        log.info("[ImpactAnalyzer] Intent={} for: '{}'",
                intent, userPrompt.substring(0, Math.min(userPrompt.length(), 60)));

        // ADD_FILE bypasses all file search
        if (intent == Intent.ADD_FILE) {
            String name = extractNewComponentName(userPrompt);
            String path = suggestNewFilePath(name, userPrompt);
            log.info("[ImpactAnalyzer] ADD_FILE → suggested path: {}", path);
            return new AnalysisResult(intent, Set.of(), path, name, 1.0);
        }

        // RESTYLE: keyword-based style file detection — more reliable than
        // embedding similarity for visual change requests
        if (intent == Intent.RESTYLE) {
            Set<String> styleFiles = detectStyleFiles(projectId, userPrompt);
            if (!styleFiles.isEmpty()) {
                log.info("[ImpactAnalyzer] RESTYLE → style-aware detection found {} file(s): {}",
                        styleFiles.size(), styleFiles);
                double confidence = computeAverageScore(styleFiles);
                return new AnalysisResult(intent, styleFiles, null, null, confidence);
            }
            log.warn("[ImpactAnalyzer] RESTYLE style detection found nothing — falling back to embedding search");
        }

        // All other intents: embedding similarity search
        double threshold = thresholdStore.computeAndRecord(projectId);
        Set<String> direct = findByEmbeddingSimilarity(projectId, userPrompt, threshold);

        if (direct.isEmpty()) {
            log.warn("[ImpactAnalyzer] No impacted files found for project {}", projectId);
            return new AnalysisResult(intent, Set.of(), null, null, 0.0);
        }

        Set<String> expanded = expandViaImportGraph(projectId, direct);

        // RESTYLE fallback: narrow to only visual files
        Set<String> finalSet = intent == Intent.RESTYLE
                ? expanded.stream()
                .filter(f -> f.endsWith(".jsx") || f.endsWith(".tsx")
                        || f.endsWith(".css") || f.endsWith(".scss"))
                .collect(Collectors.toCollection(LinkedHashSet::new))
                : expanded;

        double confidence = computeAverageScore(finalSet);
        log.info("[ImpactAnalyzer] Final set ({} files) confidence={} ({})",
                finalSet.size(), String.format("%.2f", confidence),
                new AnalysisResult(intent, finalSet, null, null, confidence).confidenceLabel());

        return new AnalysisResult(intent, finalSet, null, null, confidence);
    }

    public Set<String> detectImpactedFiles(String projectId, String userPrompt) {
        return analyze(projectId, userPrompt).impactedFiles();
    }

    // ═════════════════════════════════════════════════════════════
    //  STYLE-AWARE FILE DETECTION
    // ═════════════════════════════════════════════════════════════

    private Set<String> detectStyleFiles(String projectId, String userPrompt) {
        // Use EmbeddingService — no more FileContentProjection or repository
        Map<String, String> allContents = embeddingService.loadAllFileContents(projectId);

        if (allContents.isEmpty()) return Set.of();

        Set<String> styleKeywords = extractStyleKeywords(userPrompt);
        log.debug("[ImpactAnalyzer] Style keywords: {}", styleKeywords);

        Map<String, Integer> fileScores = new LinkedHashMap<>();

        for (Map.Entry<String, String> entry : allContents.entrySet()) {
            String path    = entry.getKey();
            String content = entry.getValue() == null ? "" : entry.getValue().toLowerCase();

            int score = 0;
            if (path.endsWith(".css") || path.endsWith(".scss")) score += 5;
            if (content.contains("bg-") || content.contains("background")) score += 2;
            for (String kw : styleKeywords) if (content.contains(kw)) score++;

            if (score > 0) fileScores.put(path, score);
        }

        return fileScores.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(3)
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
                        "text-","padding","margin","rounded","opacity")
                .stream().filter(lower::contains).forEach(keywords::add);
        return keywords;
    }

    // ═════════════════════════════════════════════════════════════
    //  INTENT CLASSIFIER
    // ═════════════════════════════════════════════════════════════

    private static final List<Pattern> ADD_PATTERNS = List.of(
            Pattern.compile("\\b(add|create|make|build|generate|implement|new)\\b.{0,30}\\b(page|component|screen|view|section|modal|dialog|panel|layout|widget|hook|util|helper)\\b", Pattern.CASE_INSENSITIVE),
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
        int add      = countMatches(prompt, ADD_PATTERNS)      * 3;
        int restyle  = countMatches(prompt, RESTYLE_PATTERNS);
        int fix      = countMatches(prompt, FIX_PATTERNS)      * 2;
        int refactor = countMatches(prompt, REFACTOR_PATTERNS) * 2;
        log.debug("[ImpactAnalyzer] Scores — add:{} restyle:{} fix:{} refactor:{}", add, restyle, fix, refactor);
        if (add >= 3)                                                return Intent.ADD_FILE;
        if (fix > 0 && fix >= refactor && fix >= restyle)           return Intent.FIX_BUG;
        if (refactor > 0 && refactor >= fix && refactor >= restyle) return Intent.REFACTOR;
        if (restyle > 0)                                            return Intent.RESTYLE;
        return Intent.MODIFY;
    }

    private int countMatches(String prompt, List<Pattern> patterns) {
        int n = 0;
        for (Pattern p : patterns) if (p.matcher(prompt).find()) n++;
        return n;
    }

    private static final Pattern NAME_EXTRACTOR = Pattern.compile(
            "\\b(?:add|create|make|build|new|implement)\\s+(?:a\\s+)?([A-Z][a-zA-Z]+|[a-z]+(?:\\s+[a-z]+)?)\\s+(?:page|component|screen|view|section|modal|dialog|panel)",
            Pattern.CASE_INSENSITIVE
    );

    private String extractNewComponentName(String prompt) {
        Matcher m = NAME_EXTRACTOR.matcher(prompt);
        return m.find() ? toPascalCase(m.group(1).trim()) : "NewComponent";
    }

    private String suggestNewFilePath(String name, String prompt) {
        String lower = prompt.toLowerCase();
        if (lower.contains("page") || lower.contains("screen") || lower.contains("view"))
            return "src/pages/" + (name.endsWith("Page") ? name : name + "Page") + ".jsx";
        if (lower.contains("hook"))
            return "src/hooks/" + (name.startsWith("use") ? name : "use" + name) + ".js";
        if (lower.contains("util") || lower.contains("helper") || lower.contains("service"))
            return "src/utils/" + name + ".js";
        if (lower.contains("modal") || lower.contains("dialog"))
            return "src/components/modals/" + name + ".jsx";
        if (lower.contains("layout"))
            return "src/layouts/" + name + ".jsx";
        return "src/components/" + name + ".jsx";
    }

    private String toPascalCase(String input) {
        return Arrays.stream(input.split("\\s+"))
                .filter(w -> !w.isEmpty())
                .map(w -> Character.toUpperCase(w.charAt(0)) + (w.length() > 1 ? w.substring(1) : ""))
                .collect(Collectors.joining());
    }

    // ═════════════════════════════════════════════════════════════
    //  EMBEDDING SIMILARITY SEARCH
    // ═════════════════════════════════════════════════════════════

    private Set<String> findByEmbeddingSimilarity(
            String projectId, String userPrompt, double threshold
    ) {
        // Use EmbeddingService — no more manual embeddingModel.embedForResponse()
        List<EmbeddingService.SimilarFile> similar =
                embeddingService.findSimilarFiles(projectId, userPrompt, SEARCH_LIMIT);

        log.debug("[ImpactAnalyzer] threshold={} scores: {}", threshold,
                similar.stream()
                        .map(s -> s.filePath() + "=" + String.format("%.3f", s.score()))
                        .collect(Collectors.joining(", ")));

        // Cache scores for computeAverageScore()
        lastSimilarityScores.clear();
        similar.forEach(s -> {
            if (s.score() != null) lastSimilarityScores.put(s.filePath(), s.score());
        });

        Set<String> matched = similar.stream()
                .filter(s -> s.score() != null && s.score() >= threshold)
                .map(EmbeddingService.SimilarFile::filePath)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (matched.isEmpty() && !similar.isEmpty()) {
            log.warn("[ImpactAnalyzer] Nothing above threshold {} — top-{} fallback", threshold, FALLBACK_N);
            similar.stream()
                    .filter(s -> s.score() != null)
                    .sorted(Comparator.comparingDouble(s -> -s.score()))
                    .limit(FALLBACK_N)
                    .map(EmbeddingService.SimilarFile::filePath)
                    .forEach(matched::add);
        }

        log.info("[ImpactAnalyzer] Direct matches ({}): {}", matched.size(), matched);
        return matched;
    }

    private double computeAverageScore(Set<String> files) {
        if (files.isEmpty()) return 0.0;
        return files.stream()
                .mapToDouble(f -> lastSimilarityScores.getOrDefault(f, 0.0))
                .average().orElse(0.0);
    }

    // ═════════════════════════════════════════════════════════════
    //  IMPORT GRAPH EXPANSION
    //  FIX: RELATIVE_IMPORT regex is now run LINE BY LINE instead
    //  of against the full file content — eliminates StackOverflowError
    //  caused by catastrophic backtracking on large AI-generated files.
    // ═════════════════════════════════════════════════════════════

    // Matches relative imports: from './path' or from '../path'
    // Safe to run on a single line — no catastrophic backtracking possible
    private static final Pattern RELATIVE_IMPORT =
            Pattern.compile("from\\s+['\"](\\.{1,2}/[^'\"\\n]+)['\"]");

    private Set<String> expandViaImportGraph(String projectId, Set<String> direct) {
        // Use EmbeddingService — no more repository.findAllContentByProjectId()
        Map<String, String> fileContents = embeddingService.loadAllFileContents(projectId);
        if (fileContents.isEmpty()) return direct;

        Map<String, Set<String>> reverse = buildReverseImportGraph(fileContents);

        Set<String> expanded = new LinkedHashSet<>(direct);
        Queue<String> queue  = new LinkedList<>(direct);
        Map<String, Integer> depth = new HashMap<>();
        direct.forEach(f -> depth.put(f, 0));

        while (!queue.isEmpty()) {
            String current      = queue.poll();
            int    currentDepth = depth.getOrDefault(current, 0);
            if (currentDepth >= IMPORT_GRAPH_DEPTH) continue;

            for (String importer : reverse.getOrDefault(current, Set.of())) {
                if (!expanded.contains(importer)) {
                    expanded.add(importer);
                    depth.put(importer, currentDepth + 1);
                    queue.add(importer);
                    log.debug("[ImpactAnalyzer] Import graph added: {} (depth {})", importer, currentDepth + 1);
                }
            }
        }

        if (expanded.size() > direct.size())
            log.info("[ImpactAnalyzer] Import graph expanded {} → {} files", direct.size(), expanded.size());

        return expanded;
    }

    /**
     * Build reverse import graph from file contents.
     * FIX: processes content LINE BY LINE to avoid StackOverflowError.
     * The RELATIVE_IMPORT regex is safe on a single line — it's only
     * catastrophic when matched against thousands of characters at once.
     */
    private Map<String, Set<String>> buildReverseImportGraph(Map<String, String> fileContents) {
        Map<String, Set<String>> reverse = new HashMap<>();

        for (Map.Entry<String, String> entry : fileContents.entrySet()) {
            String importer = entry.getKey();
            String content  = entry.getValue();
            if (content == null) continue;

            String dir = getDirectory(importer);

            // FIX: iterate line by line — prevents StackOverflowError from
            // catastrophic backtracking on large files
            for (String line : content.split("\n")) {
                // Skip lines that don't contain 'from' — fast pre-check avoids regex entirely
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
        } catch (Exception e) { return null; }
    }

    private String getDirectory(String filePath) {
        int i = filePath.lastIndexOf('/');
        return i > 0 ? filePath.substring(0, i) : "";
    }

    private boolean isAnalyzableFile(String path) {
        return path.endsWith(".jsx") || path.endsWith(".tsx")
                || path.endsWith(".js")  || path.endsWith(".ts")
                || path.endsWith(".vue");
    }
}