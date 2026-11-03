package com.albaro.rag.demo.repository;

import com.albaro.rag.demo.entity.ChatSummary;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatSummaryRepository
        extends JpaRepository<ChatSummary, String> {
}