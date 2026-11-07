package com.smartdistribution.receivable.repository;

import java.math.BigDecimal;

/**
 * 某筆應收在有效收款下的核銷加總
 */
public record PaidAmountProjection(
        Long receivableId,
        BigDecimal paidAmount
) {
}
