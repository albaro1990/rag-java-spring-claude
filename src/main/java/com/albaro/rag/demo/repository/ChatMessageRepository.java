package com.albaro.rag.demo.repository;

import com.albaro.rag.demo.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatMessageRepository
        extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findBySessionIdOrderByCreatedAtAscIdAsc(
            String sessionId
    );

    List<ChatMessage> findTop6BySessionIdOrderByCreatedAtDescIdDesc(
            String sessionId
    );

    List<ChatMessage>
    findBySessionIdAndIdGreaterThanAndIdLessThanOrderByIdAsc(
            String sessionId,
            Long summarizedThroughId,
            Long oldestWindowMessageId
    );
}