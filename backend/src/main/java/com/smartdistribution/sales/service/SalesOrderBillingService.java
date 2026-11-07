package com.smartdistribution.sales.service;

import com.smartdistribution.masterdata.common.UnitOfMeasure;
import com.smartdistribution.masterdata.customer.entity.PaymentTerm;

import java.math.BigDecimal;
import java.util.List;

/**
 * 應收模組讀取 SO 的入口：金額與到期日都用 SO 送審時的快照，不看客戶主檔目前的設定
 */
public interface SalesOrderBillingService {

    /**
     * SO 不存在時拋出 BusinessException: SALES_ORDER_NOT_FOUND
     */
    SalesOrderForBilling findForBilling(Long salesOrderId);

    /**
     * 應收需要的 SO 資料；明細的單價以計價單位計，目前計價單位一律等於訂購單位
     */
    record SalesOrderForBilling(
            Long id,
            String soNumber,
            Long customerId,
            PaymentTerm paymentTerm,
            List<Line> lines
    ) {

        public record Line(
                Long id,
                String customerItemCode,
                UnitOfMeasure pricingUnit,
                BigDecimal unitPriceExcludingTax
        ) {
        }
    }
}
