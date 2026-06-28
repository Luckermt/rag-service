package com.rag.rag_service.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.rag.rag_service.model.document.DocumentMetadata;

public interface DocumentMetadataRepository extends JpaRepository<DocumentMetadata, UUID> {
}