package com.smartdistribution.sales.service;

import com.smartdistribution.masterdata.common.UnitOfMeasure;
import com.smartdistribution.sales.entity.DeliveryMethod;

import java.math.BigDecimal;
import java.util.List;

/**
 * 出貨模組需要的 SO 資料：交貨資料印在出貨單上，明細的單位與換算倍率是送審時的快照
 *
 * @param approved 只有已核准的 SO 能通知出貨
 */
public record SalesOrderForDelivery(
        Long id,
        String soNumber,
        boolean approved,
        Long customerId,
        String customerOrderNumber,
        DeliveryMethod deliveryMethod,
        String deliveryAddress,
        String contactName,
        String contactPhone,
        List<Line> lines
) {

    public record Line(
            Long id,
            Integer lineNo,
            Long companyItemId,
            String customerItemCode,
            UnitOfMeasure unit,
            BigDecimal conversionFactor
    ) {
    }
}
