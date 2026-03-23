package com.lovable.ai_service.regeneration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks per-project similarity thresholds.
 * If the user re-prompts immediately after a regeneration (signal: within
 * 90 seconds), we assume the threshold was too high and missed files —
 * so we lower it. Each success nudges it back up toward the default.
 */
@Slf4j
@Component
public class AdaptiveThresholdStore {

    private static final double DEFAULT_THRESHOLD = 0.20;
    private static final double MIN_THRESHOLD     = 0.08;
    private static final double MAX_THRESHOLD     = 0.35;
    private static final double DECAY             = 0.03; // lower on miss
    private static final double RECOVERY          = 0.01; // raise on success
    private static final long   REPROMPT_WINDOW_MS = 90_000;

    // projectId → threshold
    private final ConcurrentHashMap<String, Double> thresholds  = new ConcurrentHashMap<>();
    // projectId → last regeneration timestamp
    private final ConcurrentHashMap<String, Long>   lastRegenAt = new ConcurrentHashMap<>();

    public double getThreshold(String projectId) {
        return thresholds.getOrDefault(projectId, DEFAULT_THRESHOLD);
    }

    /**
     * Call this at the START of every regeneration.
     * If the user is re-prompting within the window, decay the threshold.
     */
    public double computeAndRecord(String projectId) {
        long now = System.currentTimeMillis();
        Long last = lastRegenAt.get(projectId);

        if (last != null && (now - last) < REPROMPT_WINDOW_MS) {
            double current = thresholds.getOrDefault(projectId, DEFAULT_THRESHOLD);
            double lowered = Math.max(MIN_THRESHOLD, current - DECAY);
            thresholds.put(projectId, lowered);
            log.info("[AdaptiveThreshold] project={} re-prompt detected — threshold {} → {}",
                    projectId, current, lowered);
        }

        lastRegenAt.put(projectId, now);
        return thresholds.getOrDefault(projectId, DEFAULT_THRESHOLD);
    }

    /**
     * Call this when regeneration completes without an immediate re-prompt
     * (i.e. the result was good). Nudges threshold back up.
     */
    public void recordSuccess(String projectId) {
        double current = thresholds.getOrDefault(projectId, DEFAULT_THRESHOLD);
        double raised  = Math.min(MAX_THRESHOLD, current + RECOVERY);
        if (raised != current) {
            thresholds.put(projectId, raised);
            log.debug("[AdaptiveThreshold] project={} success — threshold {} → {}",
                    projectId, current, raised);
        }
    }
}