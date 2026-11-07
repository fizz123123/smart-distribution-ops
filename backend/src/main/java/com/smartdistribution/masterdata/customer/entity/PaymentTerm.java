package com.smartdistribution.masterdata.customer.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.LocalDate;

/**
 * 客戶付款條件，新客戶預設 DUE_0，只能由財務主管修改
 * 到期日一律從 DO 的實際出貨日起算日曆日
 */
@Getter
@RequiredArgsConstructor
public enum PaymentTerm {
    DUE_0(0),
    NET_30(30),
    NET_60(60);

    private final int days;

    /**
     * 應收到期日 ＝ 實際出貨日 ＋ 天數；DUE_0 就是出貨當天
     */
    public LocalDate dueDate(LocalDate actualShipmentDate) {
        return actualShipmentDate.plusDays(days);
    }
}
