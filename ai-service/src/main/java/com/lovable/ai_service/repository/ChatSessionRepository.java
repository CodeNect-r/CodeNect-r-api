package com.lovable.ai_service.repository;

import com.lovable.ai_service.entity.ChatSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ChatSessionRepository extends JpaRepository<ChatSession, UUID> {

    List<ChatSession> findByProjectIdAndUserEmailOrderByUpdatedAtDesc(String projectId, String userEmail);
}
