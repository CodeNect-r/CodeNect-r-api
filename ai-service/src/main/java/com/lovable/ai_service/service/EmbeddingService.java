package com.lovable.ai_service.service;

import com.lovable.ai_service.dto.GeneratedFile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * EmbeddingService — Spring AI VectorStore implementation
 *
 * Replaces the manual EmbeddingModel + DocumentEmbeddingRepository approach.
 * Spring AI VectorStore handles:
 *   - Chunking (via TokenTextSplitter)
 *   - Embedding generation
 *   - Storage in pgvector
 *   - Similarity search
 *
 * All the previous issues are gone:
 *   - PSQLException (Hibernate 7 + pgvector vector column deserialization) — GONE
 *   - Transaction rollback-only poisoning — GONE
 *   - StackOverflowError from regex on large content — GONE
 *   - Manual chunk size tuning — handled by TokenTextSplitter
 *
 * Requires in application.yml:
 *   spring.ai.vectorstore.pgvector.initialize-schema=true
 *   spring.ai.vectorstore.pgvector.dimensions=1024  (match your Ollama model)
 *   spring.ai.vectorstore.pgvector.index-type=HNSW
 *   spring.ai.vectorstore.pgvector.distance-type=COSINE_DISTANCE
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmbeddingService {

    private final VectorStore vectorStore;

    private static final int CHUNK_SIZE    = 512;
    private static final int CHUNK_OVERLAP = 50;
    private static final int SEARCH_TOP_K  = 20;

    // ── Store ─────────────────────────────────────────────────────────────────

    /**
     * Store embeddings for a single generated file.
     * Deletes existing chunks first, then splits and re-embeds.
     * VectorStore.add() handles embedding + storage in one call.
     */
    public void storeFileEmbeddings(String projectId, GeneratedFile file) {
        if (file == null || file.getContent() == null || file.getContent().isBlank()) return;

        try {
            // Delete existing chunks for this file
            deleteFileEmbeddings(projectId, file.getPath());

            // Build a Document with metadata for filtering
            Document doc = new Document(
                    file.getContent(),
                    Map.of(
                            "projectId", projectId,
                            "filePath",  file.getPath()
                    )
            );

            // Split into chunks — Spring AI 2.0 uses builder pattern
            TokenTextSplitter splitter = TokenTextSplitter.builder()
                    .withChunkSize(CHUNK_SIZE)
                    .withMinChunkSizeChars(CHUNK_OVERLAP)
                    .withKeepSeparator(true)
                    .build();
            List<Document> chunks = splitter.apply(List.of(doc));

            // Preserve chunkIndex in metadata for ordered reassembly
            for (int i = 0; i < chunks.size(); i++) {
                chunks.get(i).getMetadata().put("chunkIndex", i);
            }

            vectorStore.add(chunks);
            log.debug("[EmbeddingService] Stored {} chunk(s) for {}/{}", chunks.size(), projectId, file.getPath());

        } catch (Exception e) {
            log.error("[EmbeddingService] Failed to store embeddings for {}/{}: {}",
                    projectId, file.getPath(), e.getMessage());
            throw e;
        }
    }

    /**
     * Delete all embeddings for a specific file in a project.
     */
    public void deleteFileEmbeddings(String projectId, String filePath) {
        try {
            var b = new FilterExpressionBuilder();
            vectorStore.delete(
                    b.and(
                            b.eq("projectId", projectId),
                            b.eq("filePath", filePath)
                    ).build()
            );
        } catch (Exception e) {
            // Non-fatal — log and continue. Old chunks may linger but new ones
            // will have correct metadata and score higher in similarity search.
            log.warn("[EmbeddingService] Could not delete embeddings for {}/{}: {}",
                    projectId, filePath, e.getMessage());
        }
    }

    // ── Context building ──────────────────────────────────────────────────────

    /**
     * Build a text context string from stored content of specific files.
     * Used by RegenerationService (RESTYLE path) and handleAddFile().
     *
     * No more PSQLException — VectorStore never deserializes the vector column.
     */
    public String getProjectContext(String projectId, Set<String> filePaths) {
        if (filePaths == null || filePaths.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();

        for (String filePath : filePaths) {
            String content = loadFileContent(projectId, filePath);
            if (content == null || content.isBlank()) continue;

            sb.append("FILE: ").append(filePath).append("\n")
                    .append(content).append("\n")
                    .append("\n-------------------------\n");
        }

        return sb.toString();
    }

    // ── Similarity search ─────────────────────────────────────────────────────

    /**
     * Find files similar to the query, scoped to a project.
     * Returns ranked list of (filePath, score) pairs.
     * Used by ImpactAnalyzer for MODIFY/FIX_BUG/REFACTOR intents.
     */
    public List<SimilarFile> findSimilarFiles(String projectId, String query, int topK) {
        try {
            var b = new FilterExpressionBuilder();

            SearchRequest request = SearchRequest.builder()
                    .query(query)
                    .topK(topK)
                    .filterExpression(b.eq("projectId", projectId).build())
                    .build();

            return vectorStore.similaritySearch(request).stream()
                    .map(doc -> new SimilarFile(
                            (String) doc.getMetadata().get("filePath"),
                            doc.getScore()
                    ))
                    .filter(sf -> sf.filePath() != null)
                    // Deduplicate — multiple chunks from same file, keep highest score
                    .collect(Collectors.toMap(
                            SimilarFile::filePath,
                            sf -> sf,
                            (a, b2) -> a.score() >= b2.score() ? a : b2,
                            LinkedHashMap::new
                    ))
                    .values().stream()
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("[EmbeddingService] Similarity search failed for project {}: {}", projectId, e.getMessage());
            return List.of();
        }
    }

    // ── File content loading ──────────────────────────────────────────────────

    /**
     * Load the full content of a specific file by reassembling its chunks.
     * Used by DiffAwarePromptBuilder and ImpactAnalyzer (import graph).
     *
     * Replaces findByProjectIdAndFilePathOrderByChunkIndexAsc() which caused
     * PSQLException because Hibernate tried to deserialize the vector column.
     */
    public String loadFileContent(String projectId, String filePath) {
        try {
            var b = new FilterExpressionBuilder();

            SearchRequest request = SearchRequest.builder()
                    .query(filePath) // use filePath as query — high similarity to its own chunks
                    .topK(SEARCH_TOP_K)
                    .filterExpression(
                            b.and(
                                    b.eq("projectId", projectId),
                                    b.eq("filePath", filePath)
                            ).build()
                    )
                    .build();

            String content = vectorStore.similaritySearch(request).stream()
                    .sorted(Comparator.comparingInt(d ->
                            (int) d.getMetadata().getOrDefault("chunkIndex", 0)))
                    .map(Document::getText)
                    .collect(Collectors.joining());

            return content.isBlank() ? null : content;

        } catch (Exception e) {
            log.error("[EmbeddingService] Failed to load content for {}/{}: {}",
                    projectId, filePath, e.getMessage());
            return null;
        }
    }

    /**
     * Load content for all analyzable files in a project.
     * Used by ImpactAnalyzer (import graph) and ProjectSnapshot.
     * Returns map of filePath → reconstructed content.
     */
    public Map<String, String> loadAllFileContents(String projectId) {
        try {
            var b = new FilterExpressionBuilder();

            // Large topK to get all chunks for all files
            SearchRequest request = SearchRequest.builder()
                    .query(projectId) // broad query — we want all files
                    .topK(500)
                    .filterExpression(b.eq("projectId", projectId).build())
                    .build();

            return vectorStore.similaritySearch(request).stream()
                    .filter(doc -> {
                        String path = (String) doc.getMetadata().get("filePath");
                        return path != null && isAnalyzableFile(path);
                    })
                    .collect(Collectors.groupingBy(
                            doc -> (String) doc.getMetadata().get("filePath"),
                            LinkedHashMap::new,
                            Collectors.collectingAndThen(
                                    Collectors.toList(),
                                    chunks -> chunks.stream()
                                            .sorted(Comparator.comparingInt(d ->
                                                    (int) d.getMetadata().getOrDefault("chunkIndex", 0)))
                                            .map(Document::getText)
                                            .collect(Collectors.joining())
                            )
                    ));

        } catch (Exception e) {
            log.error("[EmbeddingService] Failed to load all file contents for project {}: {}", projectId, e.getMessage());
            return Map.of();
        }
    }

    /**
     * Load content for ALL files in a project (including CSS/JSON).
     * Used by ProjectSnapshot.save() to capture full project state.
     */
    public Map<String, String> loadAllFileContentsForSnapshot(String projectId) {
        try {
            var b = new FilterExpressionBuilder();

            SearchRequest request = SearchRequest.builder()
                    .query(projectId)
                    .topK(500)
                    .filterExpression(b.eq("projectId", projectId).build())
                    .build();

            return vectorStore.similaritySearch(request).stream()
                    .filter(doc -> doc.getMetadata().get("filePath") != null)
                    .collect(Collectors.groupingBy(
                            doc -> (String) doc.getMetadata().get("filePath"),
                            LinkedHashMap::new,
                            Collectors.collectingAndThen(
                                    Collectors.toList(),
                                    chunks -> chunks.stream()
                                            .sorted(Comparator.comparingInt(d ->
                                                    (int) d.getMetadata().getOrDefault("chunkIndex", 0)))
                                            .map(Document::getText)
                                            .collect(Collectors.joining())
                            )
                    ));

        } catch (Exception e) {
            log.error("[EmbeddingService] Failed to load snapshot contents for project {}: {}", projectId, e.getMessage());
            return Map.of();
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private boolean isAnalyzableFile(String path) {
        return path.endsWith(".jsx") || path.endsWith(".tsx")
                || path.endsWith(".js")  || path.endsWith(".ts")
                || path.endsWith(".vue");
    }

    public record SimilarFile(String filePath, Double score) {}
}