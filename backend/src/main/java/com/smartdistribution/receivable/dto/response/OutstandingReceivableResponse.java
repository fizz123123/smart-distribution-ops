package com.smartdistribution.receivable.dto.response;

import com.smartdistribution.receivable.service.DueStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 登錄收款時列出的未收應收：已確認、還有未收餘額；畫面可以依到期日順序預先分配
 */
public record OutstandingReceivableResponse(
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
