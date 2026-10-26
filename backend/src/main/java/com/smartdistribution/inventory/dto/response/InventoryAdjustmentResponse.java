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

    public record Line(
            Long companyItemId,
            BigDecimal quantityChangeBase
    ) {
    }
}
