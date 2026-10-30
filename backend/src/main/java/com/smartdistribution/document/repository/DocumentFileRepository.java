package com.smartdistribution.document.repository;

import com.smartdistribution.document.entity.DocumentFile;
import com.smartdistribution.document.entity.DocumentType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DocumentFileRepository extends JpaRepository<DocumentFile, Long> {

    Optional<DocumentFile> findByDocumentTypeAndSourceId(DocumentType documentType, Long sourceId);

    boolean existsByDocumentTypeAndSourceId(DocumentType documentType, Long sourceId);
}
