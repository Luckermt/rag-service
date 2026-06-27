package com.rag.rag_service.model.document;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "documents")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentMetadata {
    @Id
    private UUID id;

    @Column(name = "file_name")
    private String fileName;

    @Enumerated(EnumType.STRING)
    private DocumentStatus status;

    @Column(name = "chunk_count")
    private Integer chunkCount;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}