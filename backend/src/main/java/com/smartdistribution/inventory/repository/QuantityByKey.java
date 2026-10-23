package com.smartdistribution.inventory.repository;

import java.math.BigDecimal;

/**
 * 分組加總查詢的結果，key 依查詢而定（公司料號 ID 或預留 ID）
 */
public record QuantityByKey(Long key, BigDecimal quantity) {
}
