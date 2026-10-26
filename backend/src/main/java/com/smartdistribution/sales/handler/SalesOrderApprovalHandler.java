package com.smartdistribution.sales.handler;

import com.smartdistribution.approval.entity.ApprovalTargetType;
import com.smartdistribution.approval.handler.ApprovalHandler;
import com.smartdistribution.approval.handler.ApprovalTarget;
import com.smartdistribution.inventory.service.InventoryService;
import com.smartdistribution.inventory.service.ReservationLine;
import com.smartdistribution.masterdata.common.MasterDataStatus;
import com.smartdistribution.masterdata.customer.entity.Customer;
import com.smartdistribution.masterdata.customeritemmapping.entity.CustomerItemMapping;
import com.smartdistribution.masterdata.customerprice.entity.CustomerPrice;
import com.smartdistribution.masterdata.customerprice.entity.CustomerPriceStatus;
import com.smartdistribution.sales.entity.SalesOrder;
import com.smartdistribution.sales.entity.SalesOrderLine;
import com.smartdistribution.sales.entity.SalesOrderStatus;
import com.smartdistribution.sales.repository.SalesOrderLineRepository;
import com.smartdistribution.sales.repository.SalesOrderRepository;
import com.smartdistribution.sales.service.SalesOrderMasterData;
import com.smartdistribution.shared.exception.BusinessException;
import com.smartdistribution.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class SalesOrderApprovalHandler implements ApprovalHandler {

    private final SalesOrderRepository salesOrderRepository;
    private final SalesOrderLineRepository salesOrderLineRepository;
    private final SalesOrderMasterData salesOrderMasterData;
    private final InventoryService inventoryService;

    @Override
    public ApprovalTargetType targetType() {
        return ApprovalTargetType.SALES_ORDER;
    }

    //顯示名稱範例：SO-000001 CUS-000003 大成工程 含稅 2,100 元
    @Override
    public ApprovalTarget loadTarget(Long targetId) {
        SalesOrder salesOrder = findSalesOrder(targetId);
        Customer customer = salesOrderMasterData.findCustomer(salesOrder.getCustomerId());

        String label = salesOrder.getSoNumber() + " "
                + customer.getCustomerCode() + " " + customer.getName()
                + " 含稅 " + NumberFormat.getIntegerInstance(Locale.TAIWAN).format(salesOrder.getTotalAmount()) + " 元";

        List<String> warnings = salesOrder.getStatus() == SalesOrderStatus.PENDING_APPROVAL
                ? buildWarnings(findLines(targetId))
                : List.of();

        return new ApprovalTarget(salesOrder.getCreatedBy(), label, warnings);
    }

    /**
     * 核准：
     * 1. 客戶、客戶料號對應與公司料號仍須啟用
     * 2. 引用價格已撤銷時拒絕核准；被新版取代不擋，仍使用送審快照
     * 3. 依當下可用量建立預留，不改變可出貨實體數量
     * 任何一步失敗，核准框架會讓整個核准回滾，SO 維持待核准、不留下預留
     */
    @Override
    public void onApproved(Long targetId, Long approverId, Long approvalRequestId) {
        SalesOrder salesOrder = findSalesOrder(targetId);
        List<SalesOrderLine> lines = findLines(targetId);

        Customer customer = salesOrderMasterData.findCustomer(salesOrder.getCustomerId());
        if (customer.getStatus() != MasterDataStatus.ACTIVE) {
            throw new BusinessException(
                    ErrorCode.SO_MASTER_DATA_INACTIVE,
                    "客戶 " + customer.getCustomerCode() + " 已停用，無法核准。"
            );
        }

        for (SalesOrderLine line : lines) {
            String prefix = "第 " + line.getLineNo() + " 行 " + line.getCustomerItemCode() + "：";

            CustomerItemMapping mapping = salesOrderMasterData.findMapping(line.getCustomerItemMappingId());
            if (!salesOrderMasterData.isUsable(mapping)) {
                throw new BusinessException(
                        ErrorCode.SO_MASTER_DATA_INACTIVE,
                        prefix + "客戶料號對應或公司料號已停用，無法核准。"
                );
            }

            Optional<CustomerPrice> price = salesOrderMasterData.findPrice(line.getCustomerPriceId());
            if (price.isPresent() && price.get().getStatus() == CustomerPriceStatus.REVOKED) {
                throw new BusinessException(
                        ErrorCode.SO_PRICE_REVOKED,
                        prefix + "引用的價格已撤銷，請退回後由業務依目前價格重新送審。"
                );
            }
        }

        inventoryService.reserveForSalesOrder(
                lines.stream()
                        .map(line -> new ReservationLine(
                                line.getId(),
                                salesOrder.getId(),
                                salesOrder.getSoNumber(),
                                line.getCompanyItemId(),
                                line.getBaseQty()
                        ))
                        .toList(),
                approvalRequestId,
                approverId
        );

        salesOrder.approve(Instant.now());
    }

    @Override
    public void onReturned(Long targetId, Long approverId) {
        findSalesOrder(targetId).returnForRevision();
    }

    /**
     * 核准前給主管的提示：
     * - 引用價格已被新版取代：仍以送審時的價格核准
     * - 依目前可用量試算可預留多少；只是提示，核准時會鎖定後重新計算
     */
    private List<String> buildWarnings(List<SalesOrderLine> lines) {
        List<String> warnings = new ArrayList<>();

        for (SalesOrderLine line : lines) {
            Optional<CustomerPrice> price = salesOrderMasterData.findPrice(line.getCustomerPriceId());
            if (price.isPresent() && price.get().getStatus() == CustomerPriceStatus.REPLACED) {
                warnings.add("第 " + line.getLineNo() + " 行 " + line.getCustomerItemCode()
                        + "：價格已更新，本單仍以送審時的 "
                        + line.getUnitPriceExcludingTax().toPlainString() + " 元核准");
            }
        }

        Map<Long, BigDecimal> available = new HashMap<>(inventoryService.findAvailableQty(
                lines.stream().map(SalesOrderLine::getCompanyItemId).distinct().toList()
        ));

        //同一料號有多筆明細時依行號分配，和實際預留的規則一致
        for (SalesOrderLine line : lines) {
            BigDecimal remaining = available.getOrDefault(line.getCompanyItemId(), BigDecimal.ZERO)
                    .max(BigDecimal.ZERO);
            BigDecimal reservable = remaining.min(line.getBaseQty());
            available.put(line.getCompanyItemId(), remaining.subtract(reservable));

            if (reservable.compareTo(line.getBaseQty()) < 0) {
                warnings.add("第 " + line.getLineNo() + " 行 " + line.getCustomerItemCode()
                        + "：目前可預留 " + reservable.stripTrailingZeros().toPlainString()
                        + "，尚缺 " + line.getBaseQty().subtract(reservable).stripTrailingZeros().toPlainString()
                        + "（庫存基準單位）");
            }
        }

        return warnings;
    }

    private SalesOrder findSalesOrder(Long salesOrderId) {
        return salesOrderRepository.findById(salesOrderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SALES_ORDER_NOT_FOUND));
    }

    private List<SalesOrderLine> findLines(Long salesOrderId) {
        return salesOrderLineRepository.findAllBySalesOrderIdOrderByLineNoAsc(salesOrderId);
    }
}
