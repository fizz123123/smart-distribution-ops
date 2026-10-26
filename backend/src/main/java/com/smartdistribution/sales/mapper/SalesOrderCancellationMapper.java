package com.smartdistribution.sales.mapper;

import com.smartdistribution.sales.dto.response.SalesOrderCancellationResponse;
import com.smartdistribution.sales.entity.SalesOrderCancellation;
import com.smartdistribution.sales.entity.SalesOrderCancellationLine;
import com.smartdistribution.sales.entity.SalesOrderLine;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 只做欄位轉換；明細由 service 先查好再傳入
 */
@Component
public class SalesOrderCancellationMapper {

    public SalesOrderCancellationResponse toResponse(
            SalesOrderCancellation cancellation,
            List<SalesOrderCancellationLine> lines
    ) {
        return new SalesOrderCancellationResponse(
                cancellation.getId(),
                cancellation.getCancellationNumber(),
                cancellation.getSalesOrder().getId(),
                cancellation.getSalesOrder().getSoNumber(),
                cancellation.getStatus(),
                cancellation.getReason(),
                cancellation.getCreatedBy(),
                cancellation.getApprovedAt(),
                cancellation.getCreatedAt(),
                cancellation.getUpdatedAt(),
                cancellation.getVersion(),
                lines.stream().map(this::toLine).toList()
        );
    }

    private SalesOrderCancellationResponse.Line toLine(SalesOrderCancellationLine line) {
        SalesOrderLine salesOrderLine = line.getSalesOrderLine();

        return new SalesOrderCancellationResponse.Line(
                line.getId(),
                salesOrderLine.getId(),
                salesOrderLine.getLineNo(),
                salesOrderLine.getCustomerItemCode(),
                line.getQuantity(),
                line.getUnit(),
                line.getBaseQty()
        );
    }
}
