package com.smartdistribution.receivable.service;

import com.smartdistribution.receivable.dto.request.ConfirmPaymentRequest;
import com.smartdistribution.receivable.dto.response.PaymentDetailResponse;
import com.smartdistribution.receivable.dto.response.PaymentSummaryResponse;
import com.smartdistribution.receivable.entity.PaymentStatus;
import com.smartdistribution.shared.web.request.ReasonRequest;
import com.smartdistribution.shared.web.response.PageResponse;
import org.springframework.data.domain.Pageable;

public interface PaymentService {

    PageResponse<PaymentSummaryResponse> findPayments(
            String keyword,
            PaymentStatus status,
            Long customerId,
            Pageable pageable
    );

    PaymentDetailResponse findPayment(Long paymentId);

    /**
     * 登錄收款並核銷：應收都屬於這個客戶、已確認且未收清，每筆核銷不超過未收餘額，分配加總等於收款金額
     * 任何一項不符整筆不寫入；同一個 clientRequestId 重送時回傳原本那筆，不重複入帳
     */
    ConfirmedPayment confirmPayment(ConfirmPaymentRequest request);

    /**
     * 財務主管作廢收款：核銷失效，相關應收依剩下的有效核銷重設狀態
     */
    PaymentDetailResponse voidPayment(Long paymentId, ReasonRequest request);

    /**
     * @param created 這次新建的是 true；同一個 clientRequestId 重送、回傳原本那筆時是 false
     */
    record ConfirmedPayment(
            PaymentDetailResponse payment,
            boolean created
    ) {
    }
}
