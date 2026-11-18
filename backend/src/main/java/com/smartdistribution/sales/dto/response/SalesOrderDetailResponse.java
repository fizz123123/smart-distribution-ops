package com.smartdistribution.sales.dto.response;

import com.smartdistribution.masterdata.common.UnitOfMeasure;
import com.smartdistribution.masterdata.customer.entity.PaymentTerm;
import com.smartdistribution.pricing.service.PriceSource;
import com.smartdistribution.sales.entity.DeliveryMethod;
import com.smartdistribution.sales.entity.SalesOrderChannel;
import com.smartdistribution.sales.entity.SalesOrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * SO 詳情：草稿與退回時，倍率、基準數量、價格與金額是依目前主檔試算的預覽；
 * 送審後則是快照，主檔之後的變更不會影響
 */
public record SalesOrderDetailResponse(
        Long id,
        String soNumber,
        SalesOrderStatus status,
        SalesOrderChannel channel,
        CustomerRef customer,
        String customerOrderNumber,
        LocalDate orderDate,
        UserRef responsibleSales,
        UserRef createdBy,
        PaymentTerm paymentTerm,
        LocalDate requestedDeliveryDate,
        DeliveryMethod deliveryMethod,
        String deliveryAddress,
        String contactName,
        String contactPhone,
        String remark,
        BigDecimal untaxedAmount,
        BigDecimal taxAmount,
        BigDecimal totalAmount,
        Instant submittedAt,
        Instant approvedAt,
        Instant createdAt,
        Instant updatedAt,
        Long version,
        List<Line> lines,
        List<String> warnings //草稿與退回時列出送審前要處理的問題，例如缺少價格
) {

    public record CustomerRef(
            Long id,
            String customerCode,
            String name
    ) {
    }

    public record UserRef(
            Long id,
            String name
    ) {
    }

    public record Line(
            Long id,
            Integer lineNo,
            Long customerItemMappingId,
            String customerItemCode,
            Long companyItemId,
            String itemCode,
            String itemName,
            UnitOfMeasure baseUnit,
            BigDecimal quantity,
            UnitOfMeasure unit,
            BigDecimal conversionFactor,
            BigDecimal baseQty,
            PriceSource priceSource, //客戶價格或標準售價
            Long priceId,
            BigDecimal requestedUnitPrice, //業務填的單價，null 代表用底價
            BigDecimal listUnitPrice, //底價：草稿是依訂單日期試算，送審後是快照
            BigDecimal unitPriceExcludingTax, //成交價
            BigDecimal lineAmountExcludingTax,
            LocalDate requestedDeliveryDate, //業務填的交期，null 代表沿用表頭
            LocalDate deliveryDate, //實際交期：草稿是依表頭試算，送審後是快照
            Supply supply //核准後才有值
    ) {
    }

    /**
     * 供應狀態：全部以庫存基準單位表示
     * 已規劃未下單與採購在途來自 PO；進行中 DO 與已出貨來自 DO
     *
     * @param deliveringQty 已排入 DO、還沒過帳的數量，仍在預留裡；可再通知出貨 ＝ 預留 － 這個數量
     */
    public record Supply(
            BigDecimal reservedQty,
            BigDecimal plannedNotOrderedQty,
            BigDecimal inTransitQty,
            BigDecimal deliveringQty,
            BigDecimal shippedQty,
            BigDecimal cancelledQty,
            BigDecimal unplannedQty
    ) {
    }
}
