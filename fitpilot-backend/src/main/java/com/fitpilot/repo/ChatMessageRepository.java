package com.fitpilot.repo;

import com.fitpilot.model.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findTop40BySessionIdOrderByCreatedAtAsc(String sessionId);
}
