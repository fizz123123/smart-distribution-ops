package com.smartdistribution.masterdata.customeritemmapping.handler;

import com.smartdistribution.approval.entity.ApprovalTargetType;
import com.smartdistribution.approval.handler.ApprovalHandler;
import com.smartdistribution.approval.handler.ApprovalTarget;
import com.smartdistribution.masterdata.customeritemmapping.entity.CustomerItemMapping;
import com.smartdistribution.masterdata.customeritemmapping.repository.CustomerItemMappingRepository;
import com.smartdistribution.shared.exception.BusinessException;
import com.smartdistribution.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class CustomerItemMappingApprovalHandler implements ApprovalHandler {

    private final CustomerItemMappingRepository customerItemMappingRepository;

    @Override
    public ApprovalTargetType targetType() {
        return ApprovalTargetType.CUSTOMER_ITEM_MAPPING;
    }

    //顯示範例：CUS-000003 A-100 → PE-12-10
    @Override
    public ApprovalTarget loadTarget(Long targetId) {
        CustomerItemMapping mapping = findMapping(targetId);

        return new ApprovalTarget(
                mapping.getCreatedBy(),
                mapping.getCustomer().getCustomerCode() + " "
                        + mapping.getCustomerItemCode() + " → "
                        + mapping.getCompanyItem().getItemCode(),
                List.of()
        );
    }

    //待核准期間客戶或公司料號可能被停用，核准時重新確認；拋出例外會讓整筆核准回滾
    @Override
    public void onApproved(Long targetId, Long approverId) {
        CustomerItemMapping mapping = findMapping(targetId);

        if (!mapping.hasActiveReferences()) {
            throw new BusinessException(ErrorCode.CUSTOMER_ITEM_MAPPING_REFERENCE_INACTIVE);
        }

        mapping.approve();
    }

    @Override
    public void onReturned(Long targetId, Long approverId) {
        findMapping(targetId).returnForRevision();
    }

    private CustomerItemMapping findMapping(Long mappingId) {
        return customerItemMappingRepository.findById(mappingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CUSTOMER_ITEM_MAPPING_NOT_FOUND));
    }
}
