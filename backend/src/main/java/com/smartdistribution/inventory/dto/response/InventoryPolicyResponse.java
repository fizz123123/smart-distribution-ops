package com.smartdistribution.inventory.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

public record InventoryPolicyResponse(
        Long companyItemId,
        BigDecimal safetyStockBaseQty,
        Instant updatedAt,
        Long version
) {
}
