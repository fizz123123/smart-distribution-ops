package com.smartdistribution.inventory.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record InventoryConsistencyResponse(
        Instant checkedAt,
        int checkedItemCount,
        List<Issue> issues //空List代表全部一致
) {

    public enum IssueType {
        ON_HAND_MISMATCH,     //on_hand_qty 不等於異動加總
        RESERVATION_MISMATCH,  //open_qty 不等於預留事件加總
        RESERVED_QTY_MISMATCH, //餘額列的 reserved_qty 不等於同料號 open_qty 加總
        OVER_RESERVED          //已預留數量大於可出貨實體數量
    }

    /**
     * @param reservationId 只有 RESERVATION_MISMATCH 有值
     * @param expected      由歷程計算出的值；OVER_RESERVED 時是可出貨實體數量
     * @param actual        目前保存的值；OVER_RESERVED 時是已預留數量
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
