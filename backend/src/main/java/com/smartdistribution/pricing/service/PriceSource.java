package com.smartdistribution.pricing.service;

/**
 * 價格從哪裡來；SO 明細存「來源＋價格 ID」，才知道 ID 指的是哪一張價格表
 */
public enum PriceSource {
    CUSTOMER_PRICE,
    STANDARD_PRICE
}
