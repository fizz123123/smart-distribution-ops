package com.smartdistribution.purchasing.dto.response;

import java.math.BigDecimal;

/**
 * @param unitPriceExcludingTax 同一供應商、料號與單位最近一次核准的未稅單價；從未採購過時為 null
 */
public record LastPriceResponse(
        BigDecimal unitPriceExcludingTax
) {
}
