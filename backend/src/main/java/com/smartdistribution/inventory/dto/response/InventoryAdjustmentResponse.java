package com.smartdistribution.inventory.dto.response;

import com.smartdistribution.inventory.entity.AdjustmentReasonType;
import com.smartdistribution.inventory.entity.AdjustmentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record InventoryAdjustmentResponse(
        Long id,
        String adjustmentNumber,
        AdjustmentReasonType reasonType,
        String description,
        AdjustmentStatus status,
        Long reversalOfId,
        Long postedBy,
        Instant postedAt,
        Long version,
        List<Line> lines
) {

    /**
     * @param reversalOfLineId           更正單的明細：沖銷的是哪一筆原明細；一般調整是 null
     * @param reversedByAdjustmentId     原明細已被沖銷時，沖銷它的更正單；尚未沖銷是 null
     * @param reversedByAdjustmentNumber 同上，更正單編號
     */
    public record Line(
            Long id,
            Long companyItemId,
            BigDecimal quantityChangeBase,
            Long reversalOfLineId,
            Long reversedByAdjustmentId,
            String reversedByAdjustmentNumber
    ) {
    }
}
