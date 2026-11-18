package com.smartdistribution.sales.dto.response;

/**
 * 訂單確認單的下載內容；隨時產生、不存檔
 *
 * @param fileName 例如 SO-000001-confirmation-v2.pdf
 */
public record SalesOrderConfirmationFile(
        String fileName,
        byte[] content
) {
}
