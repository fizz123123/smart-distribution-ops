package com.smartdistribution.pricing.service;

/**
 * 價格從哪裡來；SO 明細存「來源＋價格 ID」，才知道 ID 指的是哪一張價格表
 */
public enum PriceSource {
    CUSTOMER_PRICE,
    STANDARD_PRICE;

    /**
     * 業務能不能在 SO 上調整單價：標準售價是公開定價，可以往上調；客戶價格是和客戶議定的價格，不能改
     *
     * @return 可以調整時為 true
     */
    public boolean isUnitPriceEditable() {
        return this == STANDARD_PRICE;
    }
}
