package com.smartdistribution.sales.service.impl;

import com.smartdistribution.approval.entity.ApprovalTargetType;
import com.smartdistribution.approval.service.ApprovalService;
import com.smartdistribution.identity.service.UserDirectoryService;
import com.smartdistribution.masterdata.common.MasterDataStatus;
import com.smartdistribution.masterdata.companyitem.entity.CompanyItem;
import com.smartdistribution.masterdata.customer.entity.Customer;
import com.smartdistribution.masterdata.customeritemmapping.entity.CustomerItemMapping;
import com.smartdistribution.masterdata.customerprice.entity.CustomerPrice;
import com.smartdistribution.sales.dto.request.CancelSalesOrderRequest;
import com.smartdistribution.sales.dto.request.CreateSalesOrderRequest;
import com.smartdistribution.sales.dto.request.SalesOrderLineRequest;
import com.smartdistribution.sales.dto.request.SubmitSalesOrderRequest;
import com.smartdistribution.sales.dto.request.UpdateSalesOrderRequest;
import com.smartdistribution.sales.dto.response.SalesOrderDetailResponse;
import com.smartdistribution.sales.dto.response.SalesOrderSummaryResponse;
import com.smartdistribution.sales.entity.DeliveryMethod;
import com.smartdistribution.sales.entity.SalesOrder;
import com.smartdistribution.sales.entity.SalesOrderChannel;
import com.smartdistribution.sales.entity.SalesOrderLine;
import com.smartdistribution.sales.entity.SalesOrderStatus;
import com.smartdistribution.sales.mapper.SalesOrderAssembler;
import com.smartdistribution.sales.repository.SalesOrderLineRepository;
import com.smartdistribution.sales.repository.SalesOrderRepository;
import com.smartdistribution.sales.service.SalesOrderAmounts;
import com.smartdistribution.sales.service.SalesOrderMasterData;
import com.smartdistribution.sales.service.SalesOrderService;
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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SalesOrderServiceImpl implements SalesOrderService {

    private final SalesOrderRepository salesOrderRepository;
    private final SalesOrderLineRepository salesOrderLineRepository;
    private final SalesOrderMasterData salesOrderMasterData;
    private final SalesOrderAssembler salesOrderAssembler;
    private final ApprovalService approvalService;
    private final UserDirectoryService userDirectoryService;
    private final CurrentUserProvider currentUserProvider;

    @Override
    @PreAuthorize("hasAuthority('SO_VIEW')")
    public PageResponse<SalesOrderSummaryResponse> findSalesOrders(
            String keyword,
            SalesOrderStatus status,
            Long customerId,
            Pageable pageable
    ) {
        String normalizedKeyword = keyword == null || keyword.isBlank() ? null : keyword.strip();

        return PageResponse.from(salesOrderAssembler.toSummaries(
                salesOrderRepository.search(normalizedKeyword, status, customerId, pageable)
        ));
    }

    @Override
    @PreAuthorize("hasAuthority('SO_VIEW')")
    public SalesOrderDetailResponse findSalesOrder(Long salesOrderId) {
        return salesOrderAssembler.toDetail(findSalesOrderById(salesOrderId));
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('SO_CREATE')")
    public SalesOrderDetailResponse createSalesOrder(CreateSalesOrderRequest request) {
        Long currentUserId = currentUserProvider.getCurrentUserId();
        Customer customer = findActiveCustomer(request.customerId());
        Long responsibleSalesUserId = resolveResponsibleSales(request.responsibleSalesUserId(), currentUserId);
        validateDraftLines(customer, request.lines());

        SalesOrder salesOrder = new SalesOrder(
                customer.getId(),
                responsibleSalesUserId,
                currentUserId,
                SalesOrderChannel.WEB,
                request.toDraftFields()
        );

        //SO 編號依 ID 產生，所以要先 insert 取得 id 再回填
        salesOrderRepository.saveAndFlush(salesOrder);
        salesOrder.assignNumber();
        saveLines(salesOrder, request.lines());
        salesOrderRepository.flush();

        return salesOrderAssembler.toDetail(salesOrder);
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('SO_UPDATE')")
    public SalesOrderDetailResponse updateSalesOrder(Long salesOrderId, UpdateSalesOrderRequest request) {
        SalesOrder salesOrder = findSalesOrderById(salesOrderId);
        verifyVersion(salesOrder, request.version());

        if (!salesOrder.isDraftOrReturned()) {
            throw new BusinessException(ErrorCode.SO_STATUS_CONFLICT);
        }

        Customer customer = findActiveCustomer(request.customerId());
        Long responsibleSalesUserId = resolveResponsibleSales(
                request.responsibleSalesUserId(),
                currentUserProvider.getCurrentUserId()
        );
        validateDraftLines(customer, request.lines());

        //明細整批取代：先刪除舊明細再寫入，行號才不會和舊資料的唯一約束衝突
        salesOrderLineRepository.deleteAllBySalesOrderId(salesOrderId);
        saveLines(salesOrder, request.lines());
        salesOrder.updateDraft(customer.getId(), responsibleSalesUserId, request.toDraftFields(), Instant.now());
        salesOrderRepository.flush();

        return salesOrderAssembler.toDetail(salesOrder);
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('SO_SUBMIT')")
    public SalesOrderDetailResponse submitSalesOrder(Long salesOrderId, SubmitSalesOrderRequest request) {
        SalesOrder salesOrder = findSalesOrderById(salesOrderId);
        verifyVersion(salesOrder, request.version());

        if (!salesOrder.isDraftOrReturned()) {
            throw new BusinessException(ErrorCode.SO_STATUS_CONFLICT);
        }

        ensureDeliveryInfoComplete(salesOrder);

        List<SalesOrderLine> lines =
                salesOrderLineRepository.findAllBySalesOrderIdOrderByLineNoAsc(salesOrderId);
        if (lines.isEmpty()) {
            throw new BusinessException(ErrorCode.SO_LINES_REQUIRED);
        }

        Customer customer = salesOrderMasterData.findCustomer(salesOrder.getCustomerId());
        if (customer.getStatus() != MasterDataStatus.ACTIVE) {
            throw new BusinessException(
                    ErrorCode.SO_MASTER_DATA_INACTIVE,
                    "客戶 " + customer.getCustomerCode() + " 已停用，無法送審。"
            );
        }

        //每筆明細依目前主檔寫入快照；任何一筆不通過，整張 SO 都不會送審
        List<BigDecimal> lineAmounts = new ArrayList<>();
        for (SalesOrderLine line : lines) {
            line.applySnapshot(buildSnapshot(line));
            lineAmounts.add(line.getLineAmountExcludingTax());
        }

        salesOrder.submit(
                customer.getPaymentTerm(),
                SalesOrderAmounts.documentAmounts(lineAmounts),
                Instant.now()
        );
        approvalService.submit(
                ApprovalTargetType.SALES_ORDER,
                salesOrderId,
                currentUserProvider.getCurrentUserId()
        );
        salesOrderRepository.flush();

        return salesOrderAssembler.toDetail(salesOrder);
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('SO_UPDATE')")
    public SalesOrderDetailResponse cancelSalesOrder(Long salesOrderId, CancelSalesOrderRequest request) {
        SalesOrder salesOrder = findSalesOrderById(salesOrderId);
        verifyVersion(salesOrder, request.version());

        salesOrder.cancelDraft();
        salesOrderRepository.flush();

        return salesOrderAssembler.toDetail(salesOrder);
    }

    /**
     * 送審快照：對應、公司料號與客戶必須啟用；單位要是基準單位或已設定換算；
     * 「對應＋訂購單位」要有 ACTIVE 價格，錯誤訊息帶行號，業務才知道要修哪一筆
     */
    private SalesOrderLine.Snapshot buildSnapshot(SalesOrderLine line) {
        CustomerItemMapping mapping = salesOrderMasterData.findMapping(line.getCustomerItemMappingId());
        CompanyItem item = mapping.getCompanyItem();
        String prefix = "第 " + line.getLineNo() + " 行 " + mapping.getCustomerItemCode() + "：";

        if (!salesOrderMasterData.isUsable(mapping)) {
            throw new BusinessException(
                    ErrorCode.SO_MASTER_DATA_INACTIVE,
                    prefix + "客戶料號對應、客戶或公司料號尚未啟用。"
            );
        }

        BigDecimal factor = salesOrderMasterData.findConversionFactor(item, line.getUnit())
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.SO_UNIT_INVALID,
                        prefix + "公司料號沒有設定「" + line.getUnit().getDisplayName() + "」的換算。"
                ));

        CustomerPrice price = salesOrderMasterData.findActivePrice(mapping.getId(), line.getUnit())
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.SO_ACTIVE_PRICE_REQUIRED,
                        prefix + "尚無已覆核的「" + line.getUnit().getDisplayName() + "」價格，請先完成價格建檔與覆核。"
                ));

        return new SalesOrderLine.Snapshot(
                item.getId(),
                mapping.getCustomerItemCode(),
                factor,
                SalesOrderAmounts.baseQty(line.getQuantity(), factor),
                price.getId(),
                price.getUnitPriceExcludingTax(),
                SalesOrderAmounts.lineAmount(line.getQuantity(), price.getUnitPriceExcludingTax()),
                SalesOrderAmounts.TAX_RATE
        );
    }

    //要求交期與交貨方式必填；外送時另外需要地址、收貨聯絡人與電話
    private void ensureDeliveryInfoComplete(SalesOrder salesOrder) {
        boolean complete = salesOrder.getRequestedDeliveryDate() != null
                && salesOrder.getDeliveryMethod() != null;

        if (complete && salesOrder.getDeliveryMethod() == DeliveryMethod.DELIVERY) {
            complete = salesOrder.getDeliveryAddress() != null
                    && salesOrder.getContactName() != null
                    && salesOrder.getContactPhone() != null;
        }

        if (!complete) {
            throw new BusinessException(ErrorCode.SO_DELIVERY_INFO_INCOMPLETE);
        }
    }

    //草稿只檢查對應屬於這個客戶、明細不重複、數量小數位符合單位；狀態、換算與價格等送審再檢查
    private void validateDraftLines(Customer customer, List<SalesOrderLineRequest> lines) {
        Set<String> keys = new HashSet<>();

        for (int index = 0; index < lines.size(); index++) {
            SalesOrderLineRequest line = lines.get(index);
            String prefix = "第 " + (index + 1) + " 行：";

            CustomerItemMapping mapping = salesOrderMasterData.findMapping(line.customerItemMappingId());
            if (!mapping.getCustomer().getId().equals(customer.getId())) {
                throw new BusinessException(
                        ErrorCode.SO_MAPPING_CUSTOMER_MISMATCH,
                        prefix + "客戶料號 " + mapping.getCustomerItemCode() + " 不屬於這個客戶。"
                );
            }

            if (!keys.add(line.customerItemMappingId() + ":" + line.unit())) {
                throw new BusinessException(
                        ErrorCode.SO_LINE_INVALID,
                        prefix + "同一客戶料號與單位只能出現一次。"
                );
            }

            int allowedScale = line.unit().getDecimalPlaces();
            if (line.quantity().stripTrailingZeros().scale() > allowedScale) {
                throw new BusinessException(
                        ErrorCode.SO_LINE_INVALID,
                        prefix + "「" + line.unit().getDisplayName() + "」最多 " + allowedScale + " 位小數。"
                );
            }
        }
    }

    private void saveLines(SalesOrder salesOrder, List<SalesOrderLineRequest> lines) {
        for (int index = 0; index < lines.size(); index++) {
            SalesOrderLineRequest line = lines.get(index);
            salesOrderLineRepository.save(new SalesOrderLine(
                    salesOrder,
                    index + 1,
                    line.customerItemMappingId(),
                    line.quantity(),
                    line.unit()
            ));
        }
    }

    //未指定時是建立者；負責業務必須是啟用中且具有業務角色的使用者
    private Long resolveResponsibleSales(Long requestedUserId, Long currentUserId) {
        Long userId = requestedUserId != null ? requestedUserId : currentUserId;

        if (!userDirectoryService.isActiveSalesUser(userId)) {
            throw new BusinessException(ErrorCode.SO_RESPONSIBLE_SALES_INVALID);
        }

        return userId;
    }

    private Customer findActiveCustomer(Long customerId) {
        Customer customer = salesOrderMasterData.findCustomer(customerId);

        if (customer.getStatus() != MasterDataStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.SO_CUSTOMER_INACTIVE);
        }

        return customer;
    }

    private SalesOrder findSalesOrderById(Long salesOrderId) {
        return salesOrderRepository.findById(salesOrderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SALES_ORDER_NOT_FOUND));
    }

    private void verifyVersion(SalesOrder salesOrder, Long expectedVersion) {
        if (!Objects.equals(salesOrder.getVersion(), expectedVersion)) {
            throw new BusinessException(ErrorCode.VERSION_CONFLICT);
        }
    }
}
