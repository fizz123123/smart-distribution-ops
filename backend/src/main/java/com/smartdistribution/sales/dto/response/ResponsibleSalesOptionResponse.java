package com.smartdistribution.sales.dto.response;

/**
 * 負責業務下拉選單的一個選項；只給姓名，不給 email 等聯絡資料
 */
public record ResponsibleSalesOptionResponse(
        Long id,
        String name
) {
}
