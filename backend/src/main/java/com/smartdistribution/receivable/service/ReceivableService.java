package com.smartdistribution.receivable.service;

import com.smartdistribution.receivable.dto.request.ConfirmReceivableRequest;
import com.smartdistribution.receivable.dto.request.CorrectExternalInvoiceRequest;
import com.smartdistribution.receivable.dto.response.OutstandingReceivableResponse;
import com.smartdistribution.receivable.dto.response.ReceivableDetailResponse;
import com.smartdistribution.receivable.dto.response.ReceivableSummaryResponse;
import com.smartdistribution.receivable.entity.ReceivableStatus;
import com.smartdistribution.shared.web.response.PageResponse;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface ReceivableService {

    /**
     * @param dueStatus 只看即將到期或逾期的；已收清的不會出現
     */
    PageResponse<ReceivableSummaryResponse> findReceivables(
            String keyword,
            ReceivableStatus status,
            Long customerId,
            DueStatus dueStatus,
            Pageable pageable
    );

    ReceivableDetailResponse findReceivable(Long receivableId);

    /**
     * 登錄收款時列出這個客戶已確認、還沒收清的應收，先到期的排前面
     */
    List<OutstandingReceivableResponse> findOutstandingReceivables(Long customerId);

    /**
     * 財務填入外部發票號碼與日期，草稿轉為正式應收；號碼轉大寫後必須是 2 個英文字母加 8 位數字，且不能重複
     */
    ReceivableDetailResponse confirmReceivable(Long receivableId, ConfirmReceivableRequest request);

    /**
     * 財務主管更正正式應收的外部發票號碼或日期，寫入稽核；金額、到期日與狀態都不變
     */
    ReceivableDetailResponse correctExternalInvoice(Long receivableId, CorrectExternalInvoiceRequest request);

    /**
     * DO 過帳時由出貨模組在同一個交易裡呼叫，建立應收草稿
     * 金額 = 實際出貨量 × SO 未稅單價快照，到期日依 SO 的付款條件快照；失敗時整個過帳回滾
     */
    void createForDelivery(PostedDelivery delivery);

    /**
     * 剛過帳的 DO；只帶實際量大於 0 的明細
     *
     * @param lines 數量以 SO 明細的單位表示
     */
    record PostedDelivery(
            Long deliveryOrderId,
            String doNumber,
            Long salesOrderId,
            LocalDate actualShipmentDate,
            List<Line> lines
    ) {

        public record Line(
                Long deliveryOrderLineId,
                Long salesOrderLineId,
                Long companyItemId,
                BigDecimal actualQuantity
        ) {
        }
    }
}
