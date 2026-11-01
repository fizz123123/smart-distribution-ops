package com.smartdistribution.document.service;

/**
 * 下載用的正式文件內容
 *
 * @param fileName 下載時的檔名；作廢的文件加上 -voided，例如 PO-000001-voided.pdf
 */
public record StoredDocument(
        String fileName,
        byte[] content
) {
}
