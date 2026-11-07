package com.smartdistribution.receivable.service.impl;

import com.smartdistribution.audit.entity.AuditActionType;
import com.smartdistribution.audit.service.AuditService;
import com.smartdistribution.receivable.dto.request.ConfirmReceivableRequest;
import com.smartdistribution.receivable.dto.request.CorrectExternalInvoiceRequest;
import com.smartdistribution.receivable.dto.response.PayableReceivableResponse;
import com.smartdistribution.receivable.dto.response.ReceivableDetailResponse;
import com.smartdistribution.receivable.dto.response.ReceivableSummaryResponse;
import com.smartdistribution.receivable.entity.Receivable;
import com.smartdistribution.receivable.entity.ReceivableLine;
import com.smartdistribution.receivable.entity.ReceivableStatus;
import com.smartdistribution.receivable.mapper.ReceivableAssembler;
import com.smartdistribution.receivable.repository.ReceivableLineRepository;
import com.smartdistribution.receivable.repository.ReceivableRepository;
import com.smartdistribution.receivable.service.DueStatus;
import com.smartdistribution.receivable.service.ReceivableService;
import com.smartdistribution.sales.entity.SalesOrder;
import com.smartdistribution.sales.service.SalesOrderAmounts;
import com.smartdistribution.sales.service.SalesOrderBillingService.SalesOrderForBilling;
import com.smartdistribution.sales.service.SalesOrderBillingService;
import com.smartdistribution.shared.exception.BusinessException;
import com.smartdistribution.shared.exception.ErrorCode;
import com.smartdistribution.shared.security.currentuser.CurrentUserProvider;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReceivableServiceImpl implements ReceivableService {

    private static final ZoneId TAIPEI = ZoneId.of("Asia/Taipei");

    //台灣統一發票號碼：2 個英文字母加 8 位數字
    private static final Pattern INVOICE_NUMBER = Pattern.compile("[A-Z]{2}\\d{8}");

    private final ReceivableRepository receivableRepository;
    private final ReceivableLineRepository receivableLineRepository;
    private final ReceivableAssembler receivableAssembler;
    private final SalesOrderBillingService salesOrderBillingService;
    private final AuditService auditService;
    private final CurrentUserProvider currentUserProvider;

    @Override
    @PreAuthorize("hasAuthority('RECEIVABLE_VIEW')")
    public PageResponse<ReceivableSummaryResponse> findReceivables(
            String keyword,
            ReceivableStatus status,
            Long customerId,
            DueStatus dueStatus,
            Pageable pageable
    ) {
        String normalizedKeyword = keyword == null || keyword.isBlank() ? null : keyword.strip();

        //依到期狀態篩選時，只看還沒收清的
        LocalDate today = LocalDate.now(TAIPEI);
        LocalDate dueFrom = dueStatus == null ? null : dueStatus.from(today);
        LocalDate dueTo = dueStatus == null ? null : dueStatus.to(today);

        return PageResponse.from(receivableAssembler.toSummaries(receivableRepository.search(
                normalizedKeyword,
                status,
                customerId,
                dueFrom,
                dueTo,
                dueStatus != null,
                pageable
        )));
    }

    @Override
    @PreAuthorize("hasAuthority('RECEIVABLE_VIEW')")
    public ReceivableDetailResponse findReceivable(Long receivableId) {
        return receivableAssembler.toDetail(findReceivableById(receivableId));
    }

    @Override
    @PreAuthorize("hasAuthority('RECEIVABLE_VIEW')")
    public List<PayableReceivableResponse> findPayableReceivables(Long customerId) {
        return receivableAssembler.toPayables(receivableRepository.findPayableByCustomerId(customerId));
    }

    @Override
    @Transactional
    public void createForDelivery(PostedDelivery delivery) {
        //DO 已經擋了重複過帳，這裡和資料庫的 unique 是最後一道保護
        if (receivableRepository.existsByDeliveryOrderId(delivery.deliveryOrderId())) {
            throw new BusinessException(ErrorCode.RECEIVABLE_ALREADY_EXISTS);
        }

        SalesOrderForBilling salesOrder = salesOrderBillingService.findForBilling(delivery.salesOrderId());
        Map<Long, SalesOrderForBilling.Line> salesOrderLines = salesOrder.lines()
                .stream()
                .collect(Collectors.toMap(SalesOrderForBilling.Line::id, Function.identity()));

        //計價單位等於訂購單位，所以實際出貨量直接就是計價數量
        List<BigDecimal> lineAmounts = delivery.lines()
                .stream()
                .map(line -> SalesOrderAmounts.lineAmount(
                        line.actualQuantity(),
                        salesOrderLines.get(line.salesOrderLineId()).unitPriceExcludingTax()
                ))
                .toList();
        SalesOrder.Amounts amounts = SalesOrderAmounts.documentAmounts(lineAmounts);

        Receivable receivable = new Receivable(
                delivery.deliveryOrderId(),
                delivery.doNumber(),
                salesOrder.id(),
                salesOrder.soNumber(),
                salesOrder.customerId(),
                salesOrder.paymentTerm(),
                delivery.actualShipmentDate(),
                amounts.untaxed(),
                amounts.tax(),
                amounts.total()
        );

        //應收編號依 ID 產生，所以要先 insert 取得 id 再回填
        receivableRepository.saveAndFlush(receivable);
        receivable.assignNumber();

        for (int index = 0; index < delivery.lines().size(); index++) {
            PostedDelivery.Line line = delivery.lines().get(index);
            SalesOrderForBilling.Line salesOrderLine = salesOrderLines.get(line.salesOrderLineId());

            receivableLineRepository.save(new ReceivableLine(
                    receivable,
                    index + 1,
                    line.deliveryOrderLineId(),
                    line.salesOrderLineId(),
                    line.companyItemId(),
                    salesOrderLine.customerItemCode(),
                    line.actualQuantity(),
                    salesOrderLine.pricingUnit(),
                    salesOrderLine.unitPriceExcludingTax(),
                    lineAmounts.get(index)
            ));
        }
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('RECEIVABLE_CONFIRM')")
    public ReceivableDetailResponse confirmReceivable(Long receivableId, ConfirmReceivableRequest request) {
        Receivable receivable = findReceivableById(receivableId);
        verifyVersion(receivable, request.version());

        //先擋狀態，已確認的不必再檢查發票
        if (receivable.isConfirmed()) {
            throw new BusinessException(ErrorCode.RECEIVABLE_STATUS_CONFLICT);
        }

        String invoiceNumber = checkInvoice(receivableId, request.externalInvoiceNumber(), request.externalInvoiceDate());
        receivable.confirm(invoiceNumber, request.externalInvoiceDate(), currentUserProvider.getCurrentUserId(), Instant.now());
        receivableRepository.flush();

        return receivableAssembler.toDetail(receivable);
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('RECEIVABLE_INVOICE_CORRECT')")
    public ReceivableDetailResponse correctExternalInvoice(Long receivableId, CorrectExternalInvoiceRequest request) {
        Receivable receivable = findReceivableById(receivableId);
        verifyVersion(receivable, request.version());

        //草稿還沒有發票，請直接確認
        if (!receivable.isConfirmed()) {
            throw new BusinessException(ErrorCode.RECEIVABLE_STATUS_CONFLICT);
        }

        String invoiceNumber = checkInvoice(receivableId, request.externalInvoiceNumber(), request.externalInvoiceDate());
        String beforeData = invoiceSnapshot(receivable);

        receivable.correctExternalInvoice(invoiceNumber, request.externalInvoiceDate());
        receivableRepository.flush();

        auditService.record(
                currentUserProvider.getCurrentUserId(),
                AuditActionType.RECEIVABLE_INVOICE_CORRECT,
                receivableId,
                "更正 " + receivable.getReceivableNumber() + " 的外部發票",
                beforeData,
                invoiceSnapshot(receivable),
                request.reason().strip()
        );

        return receivableAssembler.toDetail(receivable);
    }

    /**
     * 發票號碼去掉空白、轉大寫後檢查格式；日期不能晚於今天；號碼不能和其他應收重複
     * 要在改寫應收之前檢查：查重複時會先把待寫入的資料送進資料庫，改了再查會撞到 unique
     *
     * @return 整理過的發票號碼
     */
    private String checkInvoice(Long receivableId, String rawNumber, LocalDate invoiceDate) {
        String invoiceNumber = rawNumber.strip().toUpperCase(Locale.ROOT);

        if (!INVOICE_NUMBER.matcher(invoiceNumber).matches()) {
            throw new BusinessException(ErrorCode.RECEIVABLE_INVOICE_INVALID, "發票號碼須為 2 個英文字母加 8 位數字，例如 AB12345678。");
        }
        if (invoiceDate.isAfter(LocalDate.now(TAIPEI))) {
            throw new BusinessException(ErrorCode.RECEIVABLE_INVOICE_INVALID, "發票日期不能晚於今天。");
        }
        if (receivableRepository.existsByExternalInvoiceNumberAndIdNot(invoiceNumber, receivableId)) {
            throw new BusinessException(ErrorCode.RECEIVABLE_INVOICE_NUMBER_DUPLICATE);
        }

        return invoiceNumber;
    }

    private static String invoiceSnapshot(Receivable receivable) {
        return "externalInvoiceNumber=" + receivable.getExternalInvoiceNumber()
                + ", externalInvoiceDate=" + receivable.getExternalInvoiceDate();
    }

    private void verifyVersion(Receivable receivable, Long expectedVersion) {
        if (!Objects.equals(receivable.getVersion(), expectedVersion)) {
            throw new BusinessException(ErrorCode.VERSION_CONFLICT);
        }
    }

    private Receivable findReceivableById(Long receivableId) {
        return receivableRepository.findById(receivableId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RECEIVABLE_NOT_FOUND));
    }
}
