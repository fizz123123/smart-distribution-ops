package com.smartdistribution.analytics.shortage.repository.projection;

/**
 * 受影響 SO 明細要顯示的客戶
 */
public record CustomerNameProjection(
        Long customerId,
        String customerCode,
        String customerName
) {
}
