package com.smartdistribution.document.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * enum instance 決定 source_id 指向哪一張單據;
 * directory 決定檔案放在哪個資料夾
 */
@Getter
@RequiredArgsConstructor
public enum DocumentType {
    PURCHASE_ORDER("purchase-orders"), //source_id：purchase_order_id
    DELIVERY_ORDER("delivery-orders"); //source_id：delivery_order_id

    private final String directory;
}
