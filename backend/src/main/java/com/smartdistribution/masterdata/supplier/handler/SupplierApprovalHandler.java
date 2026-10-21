package com.smartdistribution.masterdata.supplier.handler;

import com.smartdistribution.approval.entity.ApprovalTargetType;
import com.smartdistribution.approval.handler.ApprovalHandler;
import com.smartdistribution.approval.handler.ApprovalTarget;
import com.smartdistribution.masterdata.supplier.entity.Supplier;
import com.smartdistribution.masterdata.supplier.repository.SupplierRepository;
import com.smartdistribution.shared.exception.BusinessException;
import com.smartdistribution.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class SupplierApprovalHandler implements ApprovalHandler {

    private final SupplierRepository supplierRepository;

    @Override
    public ApprovalTargetType targetType() {
        return ApprovalTargetType.SUPPLIER;
    }

    @Override
    public ApprovalTarget loadTarget(Long targetId) {
        Supplier supplier = findSupplier(targetId);

        return new ApprovalTarget(
                supplier.getCreatedBy(),
                supplier.getSupplierCode() + " " + supplier.getName(),
                List.of()
        );
    }

    @Override
    public void onApproved(Long targetId, Long approverId) {
        findSupplier(targetId).approve();
    }

    @Override
    public void onReturned(Long targetId, Long approverId) {
        findSupplier(targetId).returnForRevision();
    }

    private Supplier findSupplier(Long supplierId) {
        return supplierRepository.findById(supplierId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SUPPLIER_NOT_FOUND));
    }
}
