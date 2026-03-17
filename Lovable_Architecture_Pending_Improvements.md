# Lovable-Style SaaS Architecture -- Pending Improvements Documentation

This document outlines the remaining improvements and hardening tasks
across all existing services: - project-service - ai-service -
preview-service

This is intended as a future roadmap for production-grade stabilization
and scaling.

------------------------------------------------------------------------

# 1. PROJECT-SERVICE -- Pending Improvements

## 1.1 Ownership Authorization Check (CRITICAL)

Problem: Currently, project access does not verify that the
authenticated user owns the project.

Required Fix: Before any modification: - Verify project.ownerEmail ==
authenticated user - Throw AccessDeniedException if mismatch

Impact: Prevents users from modifying other users' projects.

------------------------------------------------------------------------

## 1.2 Project Processing Lock (CRITICAL)

Problem: Users can send multiple modify requests while project is
already PROCESSING.

Required Fix: If project.status == PROCESSING → block new modification
requests.

Impact: - Prevents Kafka flooding - Prevents token explosion - Avoids
race conditions

------------------------------------------------------------------------

## 1.3 Delete Project API

Add: DELETE /api/projects/{id}

Should: - Delete project - Delete project files - Delete file versions -
Publish event to preview-service to stop container

------------------------------------------------------------------------

## 1.4 Rate Limiting on Modify Endpoint

Add rate limiting (e.g., Bucket4j). Limit modification requests per user
per minute.

Impact: - Prevents abuse - Controls AI cost

------------------------------------------------------------------------

# 2. AI-SERVICE -- Pending Improvements

## 2.1 Token Usage Persistence (CRITICAL)

Currently tokens are printed only.

Add ai_usage_logs table: - projectId - userEmail - model - tokensUsed -
timestamp

Purpose: - Billing - Cost monitoring - Usage analytics

------------------------------------------------------------------------

## 2.2 Embedding Re-index on Manual File Edit

If manual edits are allowed: - Re-generate embedding for modified file -
Update vector store

Prevents stale semantic search results.

------------------------------------------------------------------------

## 2.3 Diff-Based File Patching (Advanced)

Current behavior: Full file replacement.

Future improvement: AI returns patch (diff). System applies patch
safely.

Benefits: - Safer edits - Smaller updates - Cleaner version history

------------------------------------------------------------------------

## 2.4 Prompt Versioning

Add PromptVersion enum. Store prompt version in usage logs.

Benefits: - A/B testing prompts - Rollback broken prompt strategies -
Performance comparison

------------------------------------------------------------------------

# 3. PREVIEW-SERVICE -- Pending Improvements

## 3.1 Container Log Streaming API

Add: GET /preview/{projectId}/logs

Should: - Return docker container logs - Help debug build/runtime errors

------------------------------------------------------------------------

## 3.2 Reverse Proxy Routing (Production Level)

Current: http://localhost:PORT

Future: projectId.preview.yourdomain.com

Requires: - Nginx or Traefik - Dynamic routing - Domain mapping

------------------------------------------------------------------------

## 3.3 Kubernetes Migration (Future Scale)

Replace raw Docker orchestration with: - Kubernetes deployments -
Horizontal scaling - Resource quotas - Namespace isolation

------------------------------------------------------------------------

# 4. SYSTEM-WIDE IMPROVEMENTS

## 4.1 Async Progress Updates

Currently: User waits blindly during modification.

Add: - WebSocket streaming OR - Polling status endpoint

------------------------------------------------------------------------

## 4.2 Observability & Monitoring

Add: - Centralized logging - Metrics (Prometheus) - Distributed tracing
(OpenTelemetry)

------------------------------------------------------------------------

## 4.3 Billing & Quota System (Future)

Track: - Token usage - Build time - Active containers - Storage used

Implement: - Free tier limits - Paid tier scaling

------------------------------------------------------------------------

# Recommended Order of Implementation

Phase 1 -- Security & Stability (Do First) 1. Ownership check 2.
Processing lock 3. Token logging

Phase 2 -- Developer Experience 4. Log streaming 5. Async progress
updates

Phase 3 -- Scale & Production 6. Reverse proxy 7. Kubernetes migration
8. Billing system

------------------------------------------------------------------------

END OF DOCUMENT
