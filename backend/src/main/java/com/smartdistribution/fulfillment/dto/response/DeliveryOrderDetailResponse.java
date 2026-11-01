package com.smartdistribution.fulfillment.dto.response;

import com.smartdistribution.fulfillment.entity.DeliveryOrderStatus;
import com.smartdistribution.masterdata.common.UnitOfMeasure;
import com.smartdistribution.sales.entity.DeliveryMethod;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * DO 詳情：業務與倉管都看得到，所以不含任何價格
 */
public record DeliveryOrderDetailResponse(
        Long id,
        String doNumber,
        DeliveryOrderStatus status,
        SalesOrderRef salesOrder,
        CustomerRef customer,
        Delivery delivery,
        LocalDate plannedShipmentDate,
        String remark,
        UserRef createdBy,
        Voidance voidance,
        Instant createdAt,
        Instant updatedAt,
        Long version,
        List<Line> lines
) {

    public record SalesOrderRef(
            Long id,
            String soNumber,
            String customerOrderNumber
    ) {
    }

    public record CustomerRef(
            Long id,
            String customerCode,
            String name
    ) {
    }

    //交貨資料取自 SO
    public record Delivery(
            DeliveryMethod method,
            String address,
            String contactName,
            String contactPhone
    ) {
    }

    public record UserRef(
            Long id,
            String name
    ) {
    }

    //撤回或作廢的紀錄；沒有發生時整個是 null
    public record Voidance(
            String reason,
            UserRef by,
            Instant at
    ) {
    }

    /**
     * 數量以 SO 明細的單位表示；baseQty 是庫存基準單位
     *
     * @param actualQuantity 完成揀貨後才有值；0 代表這一筆這次不出
     */
    public record Line(
            Long id,
            Integer lineNo,
            Long salesOrderLineId,
            Integer salesOrderLineNo,
            Long companyItemId,
            String itemCode,
            String itemName,
            String customerItemCode,
            UnitOfMeasure unit,
            BigDecimal plannedQuantity,
            BigDecimal plannedBaseQty,
            BigDecimal actualQuantity,
            BigDecimal actualBaseQty
    ) {
    }
}
