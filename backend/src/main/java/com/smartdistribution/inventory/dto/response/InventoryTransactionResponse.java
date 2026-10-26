package com.smartdistribution.inventory.dto.response;

import com.smartdistribution.inventory.entity.InventoryTransactionType;

import java.math.BigDecimal;
import java.time.Instant;

public record InventoryTransactionResponse(
        Long id,
        InventoryTransactionType transactionType,
        BigDecimal quantityChange,
        Long sourceId, //依異動類型指向來源單據，例如調整異動指向 inventory_adjustment
        Long actorId,
        Instant occurredAt
) {
}
