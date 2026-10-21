package com.smartdistribution.inventory.service.impl;

import com.smartdistribution.audit.entity.AuditActionType;
import com.smartdistribution.audit.service.AuditService;
import com.smartdistribution.inventory.dto.request.UpdateInventoryPolicyRequest;
import com.smartdistribution.inventory.dto.response.InventoryPolicyResponse;
import com.smartdistribution.inventory.entity.InventoryBalance;
import com.smartdistribution.inventory.entity.InventoryPolicy;
import com.smartdistribution.inventory.repository.InventoryBalanceRepository;
import com.smartdistribution.inventory.repository.InventoryPolicyRepository;
import com.smartdistribution.inventory.service.InventoryService;
import com.smartdistribution.shared.exception.BusinessException;
import com.smartdistribution.shared.exception.ErrorCode;
import com.smartdistribution.shared.security.currentuser.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InventoryServiceImpl implements InventoryService {

    private final InventoryPolicyRepository inventoryPolicyRepository;
    private final InventoryBalanceRepository inventoryBalanceRepository;
    private final AuditService auditService;
    private final CurrentUserProvider currentUserProvider;

    @Override
    @Transactional
    public void initializeForCompanyItem(Long companyItemId) {
        inventoryPolicyRepository.save(new InventoryPolicy(companyItemId));
        inventoryBalanceRepository.save(new InventoryBalance(companyItemId));
    }

    @Override
    @PreAuthorize("hasAuthority('COMPANY_ITEM_VIEW')")
    public InventoryPolicyResponse findInventoryPolicy(Long companyItemId) {
        return toResponse(findPolicy(companyItemId));
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('INVENTORY_POLICY_MANAGE')")
    public InventoryPolicyResponse updateInventoryPolicy(
            Long companyItemId,
            UpdateInventoryPolicyRequest request
    ) {
        InventoryPolicy policy = findPolicy(companyItemId);

        if (!Objects.equals(policy.getVersion(), request.version())) {
            throw new BusinessException(ErrorCode.VERSION_CONFLICT);
        }

        BigDecimal beforeQty = policy.getSafetyStockBaseQty();
        policy.changeSafetyStock(request.safetyStockBaseQty());
        inventoryPolicyRepository.flush();

        // 目標用公司料號，查一個料號的稽核紀錄就能看到安全庫存的異動
        auditService.record(
                currentUserProvider.getCurrentUserId(),
                AuditActionType.INVENTORY_POLICY_UPDATE,
                companyItemId,
                "修改安全庫存",
                "safetyStockBaseQty=" + beforeQty.toPlainString(),
                "safetyStockBaseQty=" + policy.getSafetyStockBaseQty().toPlainString(),
                request.reason().strip()
        );

        return toResponse(policy);
    }

    // 每個公司料號建立時都會建立政策，找不到就代表料號不存在
    private InventoryPolicy findPolicy(Long companyItemId) {
        return inventoryPolicyRepository.findByCompanyItemId(companyItemId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMPANY_ITEM_NOT_FOUND));
    }

    private InventoryPolicyResponse toResponse(InventoryPolicy policy) {
        return new InventoryPolicyResponse(
                policy.getCompanyItemId(),
                policy.getSafetyStockBaseQty(),
                policy.getUpdatedAt(),
                policy.getVersion()
        );
    }
}
