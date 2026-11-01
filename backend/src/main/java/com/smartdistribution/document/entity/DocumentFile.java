package com.smartdistribution.document.entity;

import com.smartdistribution.shared.persistence.entity.TimestampedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 正式文件的登記資料；PDF 檔案本身放在 DocumentStorage，這裡只記位置、雜湊與狀態
 * 一張單據最多一份正式文件，作廢只改狀態，不建立新版本
 */
@Getter
@Entity
@Table(
        name = "document_file",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_document_file_type_source",
                columnNames = {"document_type", "source_id"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DocumentFile extends TimestampedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 30)
    private DocumentType documentType;

    //依文件種類指向對應的單據，例如 PO 的 ID；不建立外鍵
    @Column(name = "source_id", nullable = false)
    private Long sourceId;

    //單據的正式編號，例如 PO-000001
    @Column(name = "document_number", nullable = false, length = 30)
    private String documentNumber;

    @Column(name = "storage_key", nullable = false, length = 255)
    private String storageKey;

    @Column(name = "file_size", nullable = false)
    private long fileSize;

    //檔案內容的 SHA-256，之後可以用來確認檔案沒有被換掉
    @Column(nullable = false, length = 64)
    private String sha256;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DocumentStatus status;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "voided_by")
    private Long voidedBy;

    @Column(name = "voided_at")
    private Instant voidedAt;

    @Column(name = "void_reason", length = 500)
    private String voidReason;

    public DocumentFile(
            DocumentType documentType,
            Long sourceId,
            String documentNumber,
            String storageKey,
            long fileSize,
            String sha256,
            Long createdBy
    ) {
        this.documentType = documentType;
        this.sourceId = sourceId;
        this.documentNumber = documentNumber;
        this.storageKey = storageKey;
        this.fileSize = fileSize;
        this.sha256 = sha256;
        this.status = DocumentStatus.ACTIVE;
        this.createdBy = createdBy;
    }

    public boolean isVoided() {
        return status == DocumentStatus.VOIDED;
    }

    public void voidDocument(Long actorId, String reason, Instant at) {
        if (isVoided()) {
            throw new IllegalStateException("文件已經作廢：" + documentNumber);
        }

        this.status = DocumentStatus.VOIDED;
        this.voidedBy = actorId;
        this.voidReason = reason;
        this.voidedAt = at;
    }
}
