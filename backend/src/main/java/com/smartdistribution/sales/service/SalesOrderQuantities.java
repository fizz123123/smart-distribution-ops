package com.smartdistribution.sales.service;

import com.smartdistribution.sales.entity.SalesOrderCancellationStatus;
import com.smartdistribution.sales.entity.SalesOrderLine;
import com.smartdistribution.sales.repository.CancelledQuantityProjection;
import com.smartdistribution.sales.repository.SalesOrderCancellationLineRepository;
import com.smartdistribution.shared.exception.BusinessException;
import com.smartdistribution.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * SO 明細的數量計算，SO 詳情、取消申請、取消核准與給採購的需求共用：
 * 剩餘需求 ＝ 訂購基準量 － 已核准取消量 － 已出貨量：客戶還要、還沒出門的數量
 * 可取消量 ＝ 剩餘需求 － 進行中 DO 數量：已經排入出貨的不能直接取消
 */
@Component
@RequiredArgsConstructor
public class SalesOrderQuantities {

    private final SalesOrderCancellationLineRepository salesOrderCancellationLineRepository;
    private final ShipmentQuantityProvider shipmentQuantityProvider;

    // SO 明細 ID → 已核准取消的基準數量；沒有取消的明細不會出現在結果中
    public Map<Long, BigDecimal> findCancelledQty(Collection<Long> salesOrderLineIds) {
        if (salesOrderLineIds.isEmpty()) {
            return Map.of();
        }

        return salesOrderCancellationLineRepository
                .sumCancelledQty(salesOrderLineIds, SalesOrderCancellationStatus.APPROVED)
                .stream()
                .collect(Collectors.toMap(
                        CancelledQuantityProjection::salesOrderLineId,
                        CancelledQuantityProjection::cancelledQty
                ));
    }

    // SO 明細 ID → 已出貨與進行中 DO 數量；沒有 DO 的明細不會出現在結果中
    public Map<Long, ShipmentQuantity> findShipments(Collection<Long> salesOrderLineIds) {
        if (salesOrderLineIds.isEmpty()) {
            return Map.of();
        }

        return shipmentQuantityProvider.findBySalesOrderLines(salesOrderLineIds);
    }

    public BigDecimal remainingQty(
            SalesOrderLine line,
            Map<Long, BigDecimal> cancelledQtyByLine,
            Map<Long, ShipmentQuantity> shipmentsByLine
    ) {
        return line.getBaseQty()
                .subtract(cancelledQtyByLine.getOrDefault(line.getId(), BigDecimal.ZERO))
                .subtract(shipment(line, shipmentsByLine).shippedQty());
    }

    public BigDecimal cancellableQty(
            SalesOrderLine line,
            Map<Long, BigDecimal> cancelledQtyByLine,
            Map<Long, ShipmentQuantity> shipmentsByLine
    ) {
        return remainingQty(line, cancelledQtyByLine, shipmentsByLine)
                .subtract(shipment(line, shipmentsByLine).deliveringQty());
    }

    /**
     * 取消的基準數量不可超過可取消量；建立、修改、送審與核准取消申請時都會檢查
     * 錯誤訊息直接告訴使用者這一行最多還能取消多少；有數量排在 DO 裡時一併提示
     */
    public void ensureWithinCancellableQty(
            SalesOrderLine line,
            BigDecimal baseQty,
            Map<Long, BigDecimal> cancelledQtyByLine,
            Map<Long, ShipmentQuantity> shipmentsByLine
    ) {
        BigDecimal cancellableQty = cancellableQty(line, cancelledQtyByLine, shipmentsByLine);

        if (baseQty.compareTo(cancellableQty) <= 0) {
            return;
        }

        String message = "第 %d 行 %s：最多可取消 %s（庫存基準單位）。".formatted(
                line.getLineNo(),
                line.getCustomerItemCode(),
                cancellableQty.stripTrailingZeros().toPlainString()
        );

        BigDecimal deliveringQty = shipment(line, shipmentsByLine).deliveringQty();
        if (deliveringQty.signum() > 0) {
            message += "另有 %s 已排入出貨，要取消請先撤回或作廢 DO。".formatted(
                    deliveringQty.stripTrailingZeros().toPlainString()
            );
        }

        throw new BusinessException(ErrorCode.SO_CANCELLATION_QUANTITY_EXCEEDED, message);
    }

    private static ShipmentQuantity shipment(SalesOrderLine line, Map<Long, ShipmentQuantity> shipmentsByLine) {
        return shipmentsByLine.getOrDefault(line.getId(), ShipmentQuantity.NONE);
    }
}
