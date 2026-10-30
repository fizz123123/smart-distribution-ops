package com.smartdistribution.document.service;

import com.smartdistribution.document.entity.DocumentFile;
import com.smartdistribution.document.entity.DocumentType;

/**
 * 正式文件的儲存、讀取與作廢；文件模組不認得 PO 或 DO，只認得「文件種類＋單據 ID」
 */
public interface DocumentService {

    /**
     * 儲存 PDF 並登記成這張單據的正式文件；同一張單據只能有一份
     * 必須在呼叫端的交易內呼叫，交易回滾時登記資料一併回滾
     */
    DocumentFile store(DocumentType documentType, Long sourceId, String documentNumber, byte[] pdf, Long actorId);

    /**
     * 讀取正式文件；會重算檔案雜湊並和產生時的比對，不一致時不給下載
     */
    StoredDocument load(DocumentType documentType, Long sourceId);

    //單據取消時作廢；檔案保留，仍可下載
    void voidDocument(DocumentType documentType, Long sourceId, Long actorId, String reason);
}
