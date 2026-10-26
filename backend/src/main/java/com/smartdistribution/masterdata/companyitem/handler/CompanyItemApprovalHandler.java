package com.smartdistribution.masterdata.companyitem.handler;

import com.smartdistribution.approval.entity.ApprovalTargetType;
import com.smartdistribution.approval.handler.ApprovalHandler;
import com.smartdistribution.approval.handler.ApprovalTarget;
import com.smartdistribution.masterdata.companyitem.entity.CompanyItem;
import com.smartdistribution.masterdata.companyitem.repository.CompanyItemRepository;
import com.smartdistribution.shared.exception.BusinessException;
import com.smartdistribution.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class CompanyItemApprovalHandler implements ApprovalHandler {

    private final CompanyItemRepository companyItemRepository;

    @Override
    public ApprovalTargetType targetType() {
        return ApprovalTargetType.COMPANY_ITEM;
    }

    @Override
    public ApprovalTarget loadTarget(Long targetId) {
        CompanyItem item = findCompanyItem(targetId);

        return new ApprovalTarget(
                item.getCreatedBy(),
                item.getItemCode() + " " + item.getName(),
                List.of()
        );
    }

    @Override
    public void onApproved(Long targetId, Long approverId, Long approvalRequestId) {
        findCompanyItem(targetId).approve();
    }

    @Override
    public void onReturned(Long targetId, Long approverId) {
        findCompanyItem(targetId).returnForRevision();
    }

    private CompanyItem findCompanyItem(Long companyItemId) {
        return companyItemRepository.findById(companyItemId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMPANY_ITEM_NOT_FOUND));
    }
}
