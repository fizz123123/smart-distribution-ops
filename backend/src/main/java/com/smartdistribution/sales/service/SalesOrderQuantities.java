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
 * SO 明細的有效數量計算，SO 詳情、取消申請與取消核准 handler 共用：
 * 有效訂購量 ＝ 訂購基準量 － 已核准取消量
 * 可取消量   ＝ 有效訂購量 － 已出貨量
 */
@Component
@RequiredArgsConstructor
public class SalesOrderQuantities {

    private final SalesOrderCancellationLineRepository salesOrderCancellationLineRepository;

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

    //P5 改為已過帳 DO 的實際出貨量
    public BigDecimal shippedQty(SalesOrderLine line) {
        return BigDecimal.ZERO;
    }

    public BigDecimal cancellableQty(SalesOrderLine line, Map<Long, BigDecimal> cancelledQtyByLine) {
        return line.getBaseQty()
                .subtract(cancelledQtyByLine.getOrDefault(line.getId(), BigDecimal.ZERO))
                .subtract(shippedQty(line));
    }

    /**
     * 取消的基準數量不可超過可取消量；建立、修改、送審與核准取消申請時都會檢查
     * 錯誤訊息直接告訴使用者這一行最多還能取消多少
     */
    public void ensureWithinCancellableQty(
            SalesOrderLine line,
            BigDecimal baseQty,
            Map<Long, BigDecimal> cancelledQtyByLine
    ) {
        BigDecimal cancellableQty = cancellableQty(line, cancelledQtyByLine);

        if (baseQty.compareTo(cancellableQty) > 0) {
            throw new BusinessException(
                    ErrorCode.SO_CANCELLATION_QUANTITY_EXCEEDED,
                    "第 %d 行 %s：最多可取消 %s（庫存基準單位）。".formatted(
                            line.getLineNo(),
                            line.getCustomerItemCode(),
                            cancellableQty.stripTrailingZeros().toPlainString()
                    )
            );
        }
    }
}
