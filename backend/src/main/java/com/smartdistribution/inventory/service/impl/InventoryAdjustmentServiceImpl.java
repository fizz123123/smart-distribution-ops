package com.smartdistribution.inventory.service.impl;

import com.smartdistribution.inventory.dto.request.AdjustmentLineRequest;
import com.smartdistribution.inventory.dto.request.PostInventoryAdjustmentRequest;
import com.smartdistribution.inventory.dto.request.ReverseInventoryAdjustmentRequest;
import com.smartdistribution.inventory.dto.response.InventoryAdjustmentResponse;
import com.smartdistribution.inventory.entity.AdjustmentReasonType;
import com.smartdistribution.inventory.entity.InventoryAdjustment;
import com.smartdistribution.inventory.entity.InventoryAdjustmentLine;
import com.smartdistribution.inventory.entity.InventoryBalance;
import com.smartdistribution.inventory.entity.InventoryTransaction;
import com.smartdistribution.inventory.repository.InventoryAdjustmentLineRepository;
import com.smartdistribution.inventory.repository.InventoryAdjustmentRepository;
import com.smartdistribution.inventory.repository.InventoryReservationRepository;
import com.smartdistribution.inventory.repository.InventoryTransactionRepository;
import com.smartdistribution.inventory.service.InventoryAdjustmentService;
import com.smartdistribution.inventory.service.InventoryBalanceLocks;
import com.smartdistribution.masterdata.companyitem.entity.CompanyItem;
import com.smartdistribution.masterdata.companyitem.repository.CompanyItemRepository;
import com.smartdistribution.shared.exception.BusinessException;
import com.smartdistribution.shared.exception.ErrorCode;
import com.smartdistribution.shared.security.currentuser.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InventoryAdjustmentServiceImpl implements InventoryAdjustmentService {

    private final InventoryAdjustmentRepository inventoryAdjustmentRepository;
    private final InventoryAdjustmentLineRepository inventoryAdjustmentLineRepository;
    private final InventoryTransactionRepository inventoryTransactionRepository;
    private final InventoryReservationRepository inventoryReservationRepository;
    private final InventoryBalanceLocks inventoryBalanceLocks;
    // 只讀取料號做存在與小數位檢查，不修改主檔
    private final CompanyItemRepository companyItemRepository;
    private final CurrentUserProvider currentUserProvider;

    @Override
    @PreAuthorize("hasAnyAuthority('INVENTORY_TRANSACTION_VIEW', 'INVENTORY_ADJUSTMENT_POST')")
    public InventoryAdjustmentResponse findAdjustment(Long adjustmentId) {
        return toResponse(findAdjustmentById(adjustmentId));
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('INVENTORY_ADJUSTMENT_POST')")
    public InventoryAdjustmentResponse postAdjustment(PostInventoryAdjustmentRequest request) {
        Map<Long, BigDecimal> changes = toChanges(request.lines());
        Map<Long, CompanyItem> items = loadItems(changes);
        boolean openingBalance = request.reasonType() == AdjustmentReasonType.OPENING_BALANCE;

        if (openingBalance && changes.values().stream().anyMatch(quantity -> quantity.signum() < 0)) {
            throw new BusinessException(ErrorCode.INVENTORY_OPENING_BALANCE_NOT_ALLOWED);
        }

        Map<Long, InventoryBalance> balances = inventoryBalanceLocks.lockInItemOrder(changes.keySet());

        // 鎖定之後再檢查，兩個人同時建立同一料號的期初時，後到的會看到前一個人的異動
        if (openingBalance) {
            for (Long companyItemId : changes.keySet()) {
                if (inventoryTransactionRepository.existsByCompanyItemId(companyItemId)
                        || inventoryReservationRepository.existsByCompanyItemId(companyItemId)) {
                    throw new BusinessException(
                            ErrorCode.INVENTORY_OPENING_BALANCE_NOT_ALLOWED,
                            items.get(companyItemId).getItemCode() + " 已有庫存異動或預留，不能建立期初庫存。"
                    );
                }
            }
        }

        InventoryAdjustment adjustment = new InventoryAdjustment(
                request.reasonType(),
                request.description(),
                null,
                currentUserProvider.getCurrentUserId(),
                Instant.now()
        );

        return post(adjustment, changes, items, balances);
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('INVENTORY_ADJUSTMENT_POST')")
    public InventoryAdjustmentResponse reverseAdjustment(
            Long adjustmentId,
            ReverseInventoryAdjustmentRequest request
    ) {
        InventoryAdjustment original = findAdjustmentById(adjustmentId);

        if (!Objects.equals(original.getVersion(), request.version())) {
            throw new BusinessException(ErrorCode.VERSION_CONFLICT);
        }

        original.markReversed();

        Map<Long, BigDecimal> changes = new LinkedHashMap<>();
        for (InventoryAdjustmentLine line :
                inventoryAdjustmentLineRepository.findAllByAdjustmentIdOrderByCompanyItemIdAsc(adjustmentId)) {
            changes.put(line.getCompanyItemId(), line.getQuantityChangeBase().negate());
        }

        Map<Long, CompanyItem> items = loadItems(changes);
        Map<Long, InventoryBalance> balances = inventoryBalanceLocks.lockInItemOrder(changes.keySet());

        InventoryAdjustment reversal = new InventoryAdjustment(
                original.getReasonType(),
                "更正 " + original.getAdjustmentNumber() + "：" + request.reason().strip(),
                original.getId(),
                currentUserProvider.getCurrentUserId(),
                Instant.now()
        );

        return post(reversal, changes, items, balances);
    }

    /**
     * 檢查數量後寫入調整、明細與異動，並更新餘額；呼叫前必須已鎖定所有相關餘額列。
     * 任一料號不通過就丟出例外，整張調整都不會寫入。
     */
    private InventoryAdjustmentResponse post(
            InventoryAdjustment adjustment,
            Map<Long, BigDecimal> changes,
            Map<Long, CompanyItem> items,
            Map<Long, InventoryBalance> balances
    ) {
        changes.forEach((companyItemId, change) ->
                checkQuantity(items.get(companyItemId), balances.get(companyItemId), change));

        // 編號依 ID 產生，所以要先 insert 取得 id 再回填
        inventoryAdjustmentRepository.saveAndFlush(adjustment);
        adjustment.assignNumber();

        changes.forEach((companyItemId, change) -> {
            inventoryAdjustmentLineRepository.save(
                    new InventoryAdjustmentLine(adjustment, companyItemId, change)
            );
            inventoryTransactionRepository.save(new InventoryTransaction(
                    companyItemId,
                    change,
                    adjustment.transactionType(),
                    adjustment.getId(),
                    adjustment.getPostedBy(),
                    adjustment.getPostedAt()
            ));
            balances.get(companyItemId).change(change);
        });

        inventoryAdjustmentRepository.flush();

        return toResponse(adjustment);
    }

    // 調整後實體數量不得小於 0；調減不得讓實體數量低於有效預留（D-010）
    private void checkQuantity(CompanyItem item, InventoryBalance balance, BigDecimal change) {
        BigDecimal newOnHandQty = balance.getOnHandQty().add(change);

        if (newOnHandQty.signum() < 0) {
            throw new BusinessException(
                    ErrorCode.INVENTORY_INSUFFICIENT,
                    "%s 調整後可出貨實體數量會小於 0（目前 %s）。".formatted(
                            item.getItemCode(),
                            balance.getOnHandQty().stripTrailingZeros().toPlainString()
                    )
            );
        }

        if (change.signum() > 0) {
            return;
        }

        BigDecimal reservedQty = inventoryBalanceLocks.reservedQty(item.getId());
        if (newOnHandQty.compareTo(reservedQty) < 0) {
            List<String> salesOrderNumbers =
                    inventoryReservationRepository.findOpenSalesOrderNumbers(item.getId());

            throw new BusinessException(
                    ErrorCode.INVENTORY_RESERVATION_WOULD_BE_BREACHED,
                    "%s 調減後會低於已預留的 %s，受影響的 SO：%s。".formatted(
                            item.getItemCode(),
                            reservedQty.stripTrailingZeros().toPlainString(),
                            String.join("、", salesOrderNumbers)
                    )
            );
        }
    }

    // 轉成「料號 → 增減量」；同一料號重複或數量為 0 都視為輸入錯誤
    private Map<Long, BigDecimal> toChanges(List<AdjustmentLineRequest> lines) {
        Map<Long, BigDecimal> changes = new LinkedHashMap<>();

        for (AdjustmentLineRequest line : lines) {
            if (line.quantityChangeBase().signum() == 0
                    || changes.put(line.companyItemId(), line.quantityChangeBase()) != null) {
                throw new BusinessException(ErrorCode.INVENTORY_ADJUSTMENT_LINES_INVALID);
            }
        }

        return changes;
    }

    // 料號必須存在，數量的小數位不得超過庫存基準單位允許的位數
    private Map<Long, CompanyItem> loadItems(Map<Long, BigDecimal> changes) {
        Map<Long, CompanyItem> items = new LinkedHashMap<>();

        for (CompanyItem item : companyItemRepository.findAllById(changes.keySet())) {
            items.put(item.getId(), item);
        }

        if (items.size() != changes.size()) {
            throw new BusinessException(ErrorCode.COMPANY_ITEM_NOT_FOUND);
        }

        changes.forEach((companyItemId, change) -> {
            CompanyItem item = items.get(companyItemId);
            int allowedScale = item.getBaseUnit().getDecimalPlaces();

            if (change.stripTrailingZeros().scale() > allowedScale) {
                throw new BusinessException(
                        ErrorCode.INVENTORY_ADJUSTMENT_LINES_INVALID,
                        "%s 的單位「%s」最多 %d 位小數。".formatted(
                                item.getItemCode(),
                                item.getBaseUnit().getDisplayName(),
                                allowedScale
                        )
                );
            }
        });

        return items;
    }

    private InventoryAdjustment findAdjustmentById(Long adjustmentId) {
        return inventoryAdjustmentRepository.findById(adjustmentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVENTORY_ADJUSTMENT_NOT_FOUND));
    }

    private InventoryAdjustmentResponse toResponse(InventoryAdjustment adjustment) {
        List<InventoryAdjustmentResponse.Line> lines = inventoryAdjustmentLineRepository
                .findAllByAdjustmentIdOrderByCompanyItemIdAsc(adjustment.getId())
                .stream()
                .map(line -> new InventoryAdjustmentResponse.Line(
                        line.getCompanyItemId(),
                        line.getQuantityChangeBase()
                ))
                .toList();

        return new InventoryAdjustmentResponse(
                adjustment.getId(),
                adjustment.getAdjustmentNumber(),
                adjustment.getReasonType(),
                adjustment.getDescription(),
                adjustment.getStatus(),
                adjustment.getReversalOfId(),
                adjustment.getPostedBy(),
                adjustment.getPostedAt(),
                adjustment.getVersion(),
                lines
        );
    }
}
