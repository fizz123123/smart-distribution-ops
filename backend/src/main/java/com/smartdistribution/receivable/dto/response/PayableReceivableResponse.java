package com.smartdistribution.receivable.dto.response;

import com.smartdistribution.receivable.service.DueStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 登錄收款時列出的應收：已確認、還沒收清；畫面可以依到期日順序預先分配
 */
public record PayableReceivableResponse(
        Long id,
        String receivableNumber,
        String doNumber,
        String externalInvoiceNumber,
        LocalDate dueDate,
        DueStatus dueStatus,
        BigDecimal totalAmount,
        BigDecimal paidAmount,
        BigDecimal balance
) {
}
