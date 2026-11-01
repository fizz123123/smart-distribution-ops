package com.smartdistribution.document.service.impl;

import com.smartdistribution.document.entity.DocumentFile;
import com.smartdistribution.document.entity.DocumentType;
import com.smartdistribution.document.repository.DocumentFileRepository;
import com.smartdistribution.document.service.DocumentService;
import com.smartdistribution.document.service.StoredDocument;
import com.smartdistribution.document.storage.DocumentStorage;
import com.smartdistribution.shared.exception.BusinessException;
import com.smartdistribution.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HexFormat;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DocumentServiceImpl implements DocumentService {

    private static final ZoneId TAIPEI = ZoneId.of("Asia/Taipei");

    private final DocumentFileRepository documentFileRepository;
    private final DocumentStorage documentStorage;

    @Override
    @Transactional
    public void store(
            DocumentType documentType,
            Long sourceId,
            String documentNumber,
            byte[] pdf,
            Long actorId
    ) {
        if (documentFileRepository.existsByDocumentTypeAndSourceId(documentType, sourceId)) {
            throw new BusinessException(ErrorCode.DOCUMENT_ALREADY_EXISTS);
        }

        //例：purchase-orders/2026/PO-000001.pdf
        String storageKey = documentType.getDirectory()
                + "/" + LocalDate.now(TAIPEI).getYear()
                + "/" + documentNumber + ".pdf";

        //先登記再寫檔：登記違反唯一約束時不會留下檔案；寫檔失敗時登記隨交易回滾
        documentFileRepository.saveAndFlush(new DocumentFile(
                documentType,
                sourceId,
                documentNumber,
                storageKey,
                pdf.length,
                sha256(pdf),
                actorId
        ));
        documentStorage.save(storageKey, pdf);
    }

    @Override
    public StoredDocument load(DocumentType documentType, Long sourceId) {
        DocumentFile documentFile = findDocument(documentType, sourceId);
        byte[] content = documentStorage.read(documentFile.getStorageKey());

        //每次下載都重算雜湊，和產生當下記錄的比對；不一致代表檔案被換掉或損壞，不給下載
        String actualSha256 = sha256(content);
        if (!actualSha256.equals(documentFile.getSha256())) {
            log.error(
                    "正式文件雜湊不一致：{}，位置 {}，記錄 {}，實際 {}",
                    documentFile.getDocumentNumber(),
                    documentFile.getStorageKey(),
                    documentFile.getSha256(),
                    actualSha256
            );
            throw new BusinessException(ErrorCode.DOCUMENT_INTEGRITY_FAILED);
        }

        //作廢的檔名加上 -voided，下載到電腦後仍分得出來；用英文避免檔名在其他工具裡變亂碼
        String fileName = documentFile.isVoided()
                ? documentFile.getDocumentNumber() + "-voided.pdf"
                : documentFile.getDocumentNumber() + ".pdf";

        return new StoredDocument(fileName, content);
    }

    @Override
    @Transactional
    public void voidDocument(DocumentType documentType, Long sourceId, Long actorId, String reason) {
        findDocument(documentType, sourceId)
                .voidDocument(actorId, reason, Instant.now());
    }

    private DocumentFile findDocument(DocumentType documentType, Long sourceId) {
        return documentFileRepository.findByDocumentTypeAndSourceId(documentType, sourceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND));
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
