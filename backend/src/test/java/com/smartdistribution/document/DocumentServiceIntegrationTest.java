package com.smartdistribution.document;

import com.smartdistribution.document.config.DocumentProperties;
import com.smartdistribution.document.entity.DocumentFile;
import com.smartdistribution.document.entity.DocumentStatus;
import com.smartdistribution.document.entity.DocumentType;
import com.smartdistribution.document.repository.DocumentFileRepository;
import com.smartdistribution.document.service.DocumentService;
import com.smartdistribution.document.service.StoredDocument;
import com.smartdistribution.shared.exception.BusinessException;
import com.smartdistribution.shared.exception.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 正式文件的儲存、讀取與作廢；PDF 寫到 test profile 指定的資料夾
 */
@SpringBootTest
@ActiveProfiles("test")
class DocumentServiceIntegrationTest {

    private static final String TEST_NUMBER_PREFIX = "TEST-DOC-";
    private static final Long ACTOR_ID = 1L;

    @Autowired
    DocumentService documentService;

    @Autowired
    DocumentProperties documentProperties;

    @Autowired
    DocumentFileRepository documentFileRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanTestData() {
        jdbcTemplate.update("DELETE FROM document_file WHERE document_number LIKE ?", TEST_NUMBER_PREFIX + "%");
    }

    @Test
    void storedDocumentIsWrittenToDiskAndCanBeLoaded() throws Exception {
        Long sourceId = randomSourceId();
        String number = testNumber();
        byte[] pdf = "%PDF-test-content".getBytes(StandardCharsets.US_ASCII);

        documentService.store(DocumentType.PURCHASE_ORDER, sourceId, number, pdf, ACTOR_ID);

        DocumentFile stored = findStored(sourceId);
        assertThat(stored.getStatus()).isEqualTo(DocumentStatus.ACTIVE);
        assertThat(stored.getStorageKey()).startsWith("purchase-orders/").endsWith("/" + number + ".pdf");
        assertThat(stored.getFileSize()).isEqualTo(pdf.length);
        assertThat(stored.getSha256())
                .isEqualTo(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(pdf)));
        assertThat(Files.readAllBytes(Path.of(documentProperties.localDir(), stored.getStorageKey()))).isEqualTo(pdf);

        StoredDocument loaded = documentService.load(DocumentType.PURCHASE_ORDER, sourceId);
        assertThat(loaded.fileName()).isEqualTo(number + ".pdf");
        assertThat(loaded.content()).isEqualTo(pdf);
    }

    @Test
    void sameSourceCannotStoreTwice() {
        Long sourceId = randomSourceId();
        byte[] pdf = "%PDF-first".getBytes(StandardCharsets.US_ASCII);
        documentService.store(DocumentType.PURCHASE_ORDER, sourceId, testNumber(), pdf, ACTOR_ID);

        assertThatThrownBy(() -> documentService.store(
                DocumentType.PURCHASE_ORDER, sourceId, testNumber(), pdf, ACTOR_ID
        ))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.DOCUMENT_ALREADY_EXISTS);
    }

    @Test
    void voidedDocumentKeepsFileAndIsMarkedInFileName() {
        Long sourceId = randomSourceId();
        String number = testNumber();
        byte[] pdf = "%PDF-void".getBytes(StandardCharsets.US_ASCII);
        documentService.store(DocumentType.PURCHASE_ORDER, sourceId, number, pdf, ACTOR_ID);

        documentService.voidDocument(DocumentType.PURCHASE_ORDER, sourceId, ACTOR_ID, "供應商停產");

        StoredDocument loaded = documentService.load(DocumentType.PURCHASE_ORDER, sourceId);
        assertThat(loaded.fileName()).isEqualTo(number + "-voided.pdf");
        assertThat(loaded.content()).isEqualTo(pdf);

        String reason = jdbcTemplate.queryForObject(
                "SELECT void_reason FROM document_file WHERE document_number = ?", String.class, number
        );
        assertThat(reason).isEqualTo("供應商停產");
    }

    @Test
    void changedFileIsRejectedOnLoad() throws Exception {
        Long sourceId = randomSourceId();
        byte[] pdf = "%PDF-original".getBytes(StandardCharsets.US_ASCII);
        documentService.store(DocumentType.PURCHASE_ORDER, sourceId, testNumber(), pdf, ACTOR_ID);
        DocumentFile stored = findStored(sourceId);

        //模擬有人直接到存放位置換掉檔案
        Files.write(
                Path.of(documentProperties.localDir(), stored.getStorageKey()),
                "%PDF-tampered".getBytes(StandardCharsets.US_ASCII)
        );

        assertThatThrownBy(() -> documentService.load(DocumentType.PURCHASE_ORDER, sourceId))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.DOCUMENT_INTEGRITY_FAILED);
    }

    @Test
    void missingDocumentIsReported() {
        assertThatThrownBy(() -> documentService.load(DocumentType.PURCHASE_ORDER, randomSourceId()))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.DOCUMENT_NOT_FOUND);
    }

    //用很大的隨機 ID，避免和真正的單據撞在一起
    private DocumentFile findStored(Long sourceId) {
        return documentFileRepository.findByDocumentTypeAndSourceId(DocumentType.PURCHASE_ORDER, sourceId).orElseThrow();
    }

    private Long randomSourceId() {
        return ThreadLocalRandom.current().nextLong(9_000_000_000L, 9_999_999_999L);
    }

    private String testNumber() {
        return TEST_NUMBER_PREFIX + UUID.randomUUID().toString().substring(0, 8);
    }
}
