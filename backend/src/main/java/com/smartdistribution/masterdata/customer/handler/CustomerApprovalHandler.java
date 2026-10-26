package com.smartdistribution.masterdata.customer.handler;

import com.smartdistribution.approval.entity.ApprovalTargetType;
import com.smartdistribution.approval.handler.ApprovalHandler;
import com.smartdistribution.approval.handler.ApprovalTarget;
import com.smartdistribution.masterdata.customer.entity.Customer;
import com.smartdistribution.masterdata.customer.repository.CustomerRepository;
import com.smartdistribution.shared.exception.BusinessException;
import com.smartdistribution.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class CustomerApprovalHandler implements ApprovalHandler {

    private final CustomerRepository customerRepository;

    @Override
    public ApprovalTargetType targetType() {
        return ApprovalTargetType.CUSTOMER;
    }

    @Override
    public ApprovalTarget loadTarget(Long targetId) {
        Customer customer = findCustomer(targetId);

        return new ApprovalTarget(
                customer.getCreatedBy(),
                customer.getCustomerCode() + " " + customer.getName(),
                List.of()
        );
    }

    @Override
    public void onApproved(Long targetId, Long approverId, Long approvalRequestId) {
        findCustomer(targetId).approve();
    }

    @Override
    public void onReturned(Long targetId, Long approverId) {
        findCustomer(targetId).returnForRevision();
    }

    private Customer findCustomer(Long customerId) {
        return customerRepository.findById(customerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CUSTOMER_NOT_FOUND));
    }
}
