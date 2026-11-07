package com.smartdistribution.receivable.service.impl;

import com.smartdistribution.receivable.dto.request.ConfirmPaymentRequest;
import com.smartdistribution.receivable.dto.request.PaymentAllocationRequest;
import com.smartdistribution.receivable.dto.response.PaymentDetailResponse;
import com.smartdistribution.receivable.dto.response.PaymentSummaryResponse;
import com.smartdistribution.receivable.entity.Payment;
import com.smartdistribution.receivable.entity.PaymentAllocation;
import com.smartdistribution.receivable.entity.PaymentStatus;
import com.smartdistribution.receivable.entity.Receivable;
import com.smartdistribution.receivable.mapper.PaymentAssembler;
import com.smartdistribution.receivable.repository.PaymentAllocationRepository;
import com.smartdistribution.receivable.repository.PaymentRepository;
import com.smartdistribution.receivable.repository.ReceivableRepository;
import com.smartdistribution.receivable.service.PaymentService;
import com.smartdistribution.receivable.service.ReceivableBalances;
import com.smartdistribution.shared.exception.BusinessException;
import com.smartdistribution.shared.exception.ErrorCode;
import com.smartdistribution.shared.security.currentuser.CurrentUserProvider;
import com.smartdistribution.shared.web.request.ReasonRequest;
import com.smartdistribution.shared.web.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentServiceImpl implements PaymentService {

    private static final ZoneId TAIPEI = ZoneId.of("Asia/Taipei");

    private final PaymentRepository paymentRepository;
    private final PaymentAllocationRepository paymentAllocationRepository;
    private final ReceivableRepository receivableRepository;
    private final ReceivableBalances receivableBalances;
    private final PaymentAssembler paymentAssembler;
    private final CurrentUserProvider currentUserProvider;

    @Override
    @PreAuthorize("hasAuthority('PAYMENT_VIEW')")
    public PageResponse<PaymentSummaryResponse> findPayments(
            String keyword,
            PaymentStatus status,
            Long customerId,
            Pageable pageable
    ) {
        String normalizedKeyword = keyword == null || keyword.isBlank() ? null : keyword.strip();

        return PageResponse.from(paymentAssembler.toSummaries(
                paymentRepository.search(normalizedKeyword, status, customerId, pageable)
        ));
    }

    @Override
    @PreAuthorize("hasAuthority('PAYMENT_VIEW')")
    public PaymentDetailResponse findPayment(Long paymentId) {
        return paymentAssembler.toDetail(findPaymentById(paymentId));
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('PAYMENT_CONFIRM')")
    public ConfirmedPayment confirmPayment(ConfirmPaymentRequest request) {
        checkRequest(request);

        //先鎖應收再查重送：同一筆收款幾乎同時送兩次時，第二次會等第一次提交，再查到它
        List<Long> receivableIds = request.allocations().stream().map(PaymentAllocationRequest::receivableId).toList();
        List<Receivable> receivables = receivableRepository.findAllByIdInForUpdate(receivableIds);

        Optional<Payment> existing = paymentRepository.findByClientRequestId(request.clientRequestId());
        if (existing.isPresent()) {
            return new ConfirmedPayment(paymentAssembler.toDetail(existing.get()), false);
        }

        if (receivables.size() != receivableIds.size()) {
            throw new BusinessException(ErrorCode.RECEIVABLE_NOT_FOUND);
        }

        Map<Long, BigDecimal> paidAmounts = receivableBalances.findPaidAmounts(receivableIds);
        Map<Long, BigDecimal> allocatedByReceivable = request.allocations()
                .stream()
                .collect(Collectors.toMap(
                        PaymentAllocationRequest::receivableId,
                        PaymentAllocationRequest::allocatedAmount
                ));

        for (Receivable receivable : receivables) {
            checkPayable(receivable, request.customerId());

            BigDecimal balance = ReceivableBalances.balance(receivable, paidAmounts);
            if (allocatedByReceivable.get(receivable.getId()).compareTo(balance) > 0) {
                throw new BusinessException(
                        ErrorCode.PAYMENT_AMOUNT_EXCEEDS_BALANCE,
                        receivable.getReceivableNumber() + " 最多還能核銷 " + balance.toPlainString() + "。"
                );
            }
        }

        Payment payment = new Payment(
                request.clientRequestId(),
                request.customerId(),
                request.receivedDate(),
                request.amount(),
                request.method(),
                request.referenceNumber(),
                currentUserProvider.getCurrentUserId()
        );

        //收款編號依 ID 產生，所以要先 insert 取得 id 再回填
        paymentRepository.saveAndFlush(payment);
        payment.assignNumber();

        for (Receivable receivable : receivables) {
            BigDecimal allocated = allocatedByReceivable.get(receivable.getId());

            paymentAllocationRepository.save(new PaymentAllocation(payment, receivable, allocated));
            receivable.updatePaymentStatus(ReceivableBalances.paidAmount(receivable, paidAmounts).add(allocated));
        }
        paymentRepository.flush();

        return new ConfirmedPayment(paymentAssembler.toDetail(payment), true);
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('PAYMENT_VOID')")
    public PaymentDetailResponse voidPayment(Long paymentId, ReasonRequest request) {
        //先鎖收款表頭，再依 ID 鎖應收，和確認收款鎖應收的順序相同
        Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
        if (!Objects.equals(payment.getVersion(), request.version())) {
            throw new BusinessException(ErrorCode.VERSION_CONFLICT);
        }

        List<Long> receivableIds = paymentAllocationRepository.findAllByPaymentIdOrderByIdAsc(paymentId)
                .stream()
                .map(allocation -> allocation.getReceivable().getId())
                .toList();
        List<Receivable> receivables = receivableRepository.findAllByIdInForUpdate(receivableIds);

        payment.voidPayment(request.reason(), currentUserProvider.getCurrentUserId(), Instant.now());
        //已收金額只算 CONFIRMED 的收款，所以先把作廢寫進資料庫再重算
        paymentRepository.flush();

        Map<Long, BigDecimal> paidAmounts = receivableBalances.findPaidAmounts(receivableIds);
        for (Receivable receivable : receivables) {
            receivable.updatePaymentStatus(ReceivableBalances.paidAmount(receivable, paidAmounts));
        }
        paymentRepository.flush();

        return paymentAssembler.toDetail(payment);
    }

    /**
     * 只看 request 本身的檢查：收款日期不能晚於今天、同一筆應收只能出現一次、分配加總等於收款金額
     * 金額大於 0、整數元由 request 的驗證註解處理
     */
    private void checkRequest(ConfirmPaymentRequest request) {
        if (request.receivedDate().isAfter(LocalDate.now(TAIPEI))) {
            throw new BusinessException(ErrorCode.PAYMENT_INVALID, "收款日期不能晚於今天。");
        }

        Set<Long> seenReceivableIds = new HashSet<>();
        BigDecimal allocatedTotal = BigDecimal.ZERO;
        for (PaymentAllocationRequest allocation : request.allocations()) {
            if (!seenReceivableIds.add(allocation.receivableId())) {
                throw new BusinessException(ErrorCode.PAYMENT_INVALID, "同一筆應收只能分配一次。");
            }
            allocatedTotal = allocatedTotal.add(allocation.allocatedAmount());
        }

        //不接受溢收或未分配款
        if (allocatedTotal.compareTo(request.amount()) != 0) {
            throw new BusinessException(
                    ErrorCode.PAYMENT_INVALID,
                    "分配加總 " + allocatedTotal.toPlainString() + " 必須等於收款金額 " + request.amount().toPlainString() + "。"
            );
        }
    }

    //應收要屬於這個客戶、已確認，而且還沒收清
    private static void checkPayable(Receivable receivable, Long customerId) {
        String number = receivable.getReceivableNumber();

        if (!receivable.getCustomerId().equals(customerId)) {
            throw new BusinessException(ErrorCode.PAYMENT_RECEIVABLE_INVALID, number + " 不是這個客戶的應收。");
        }
        if (!receivable.isConfirmed()) {
            throw new BusinessException(ErrorCode.PAYMENT_RECEIVABLE_INVALID, number + " 還沒確認，不能收款。");
        }
        if (!receivable.isPayable()) {
            throw new BusinessException(ErrorCode.PAYMENT_RECEIVABLE_INVALID, number + " 已經收清。");
        }
    }

    private Payment findPaymentById(Long paymentId) {
        return paymentRepository.findById(paymentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
    }
}
