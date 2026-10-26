package com.smartdistribution.sales.dto.response;

import com.smartdistribution.masterdata.common.UnitOfMeasure;
import com.smartdistribution.sales.entity.SalesOrderCancellationStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record SalesOrderCancellationResponse(
        Long id,
        String cancellationNumber,
        Long salesOrderId,
        String soNumber,
        SalesOrderCancellationStatus status,
        String reason,
        Long createdBy,
        Instant approvedAt,
        Instant createdAt,
        Instant updatedAt,
        Long version,
        List<Line> lines
) {

    public record Line(
            Long id,
            Long salesOrderLineId,
            Integer lineNo,
            String customerItemCode,
            BigDecimal quantity, //取消數量，單位是 SO 明細的訂購單位
            UnitOfMeasure unit,
            BigDecimal baseQty //換算後的庫存基準數量
    ) {
    }
}
