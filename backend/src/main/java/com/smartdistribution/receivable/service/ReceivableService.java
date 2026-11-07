package com.smartdistribution.receivable.service;

import com.smartdistribution.receivable.dto.response.ReceivableDetailResponse;
import com.smartdistribution.receivable.dto.response.ReceivableSummaryResponse;
import com.smartdistribution.receivable.entity.ReceivableStatus;
import com.smartdistribution.shared.web.response.PageResponse;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface ReceivableService {

    PageResponse<ReceivableSummaryResponse> findReceivables(
            String keyword,
            ReceivableStatus status,
            Long customerId,
            Pageable pageable
    );

    ReceivableDetailResponse findReceivable(Long receivableId);

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
