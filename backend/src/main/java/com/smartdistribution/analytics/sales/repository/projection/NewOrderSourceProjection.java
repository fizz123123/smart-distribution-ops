package com.smartdistribution.analytics.sales.repository.projection;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 新增接單的一筆來源：一張期間內核准的 SO
 *
 * @param amount 這張 SO 的明細小計加總；有料號篩選時只算該料號的明細
 */
public record NewOrderSourceProjection(
        LocalDate approvedDate,
        Long salesOrderId,
        String soNumber,
        Long customerId,
        String customerCode,
        String customerName,
        Long responsibleSalesUserId,
        String responsibleSalesName,
        BigDecimal amount
) {
}
