package com.smartdistribution.inventory.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record InventoryConsistencyResponse(
        Instant checkedAt,
        int checkedItemCount,
        // 空清單代表全部一致
        List<Issue> issues
) {

    public enum IssueType {
        // on_hand_qty 不等於異動加總
        ON_HAND_MISMATCH,
        // open_qty 不等於預留事件加總
        RESERVATION_MISMATCH,
        // 有效預留總量大於可出貨實體數量
        OVER_RESERVED
    }

    /**
     * @param reservationId 只有 RESERVATION_MISMATCH 有值
     * @param expected      由歷程計算出的值；OVER_RESERVED 時是可出貨實體數量
     * @param actual        目前保存的值；OVER_RESERVED 時是有效預留總量
     */
    public record Issue(
            Long companyItemId,
            Long reservationId,
            IssueType issueType,
            BigDecimal expected,
            BigDecimal actual
    ) {
    }
}
