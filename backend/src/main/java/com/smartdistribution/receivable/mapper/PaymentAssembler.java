package com.smartdistribution.receivable.mapper;

import com.smartdistribution.identity.service.UserDirectoryService;
import com.smartdistribution.masterdata.customer.entity.Customer;
import com.smartdistribution.receivable.dto.response.PaymentDetailResponse;
import com.smartdistribution.receivable.dto.response.PaymentSummaryResponse;
import com.smartdistribution.receivable.entity.Payment;
import com.smartdistribution.receivable.repository.PaymentAllocationRepository;
import com.smartdistribution.receivable.service.ReceivableMasterData;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 組出收款的回應，要查客戶、使用者與核銷到的應收
 */
@Component
@RequiredArgsConstructor
public class PaymentAssembler {

    private final PaymentAllocationRepository paymentAllocationRepository;
    private final ReceivableMasterData receivableMasterData;
    private final UserDirectoryService userDirectoryService;

    //清單頁一次查好這一頁的客戶
    public Page<PaymentSummaryResponse> toSummaries(Page<Payment> payments) {
        Map<Long, Customer> customers = receivableMasterData.findCustomers(
                payments.stream().map(Payment::getCustomerId).collect(Collectors.toSet())
        );

        return payments.map(payment -> {
            Customer customer = customers.get(payment.getCustomerId());

            return new PaymentSummaryResponse(
                    payment.getId(),
                    payment.getPaymentNumber(),
                    payment.getStatus(),
                    payment.getCustomerId(),
                    customer == null ? null : customer.getCustomerCode(),
                    customer == null ? null : customer.getName(),
                    payment.getReceivedDate(),
                    payment.getAmount(),
                    payment.getMethod(),
                    payment.getReferenceNumber(),
                    payment.getCreatedAt()
            );
        });
    }

    public PaymentDetailResponse toDetail(Payment payment) {
        Customer customer = receivableMasterData.findCustomers(List.of(payment.getCustomerId()))
                .get(payment.getCustomerId());

        return new PaymentDetailResponse(
                payment.getId(),
                payment.getPaymentNumber(),
                payment.getClientRequestId(),
                payment.getStatus(),
                new PaymentDetailResponse.CustomerRef(
                        customer.getId(),
                        customer.getCustomerCode(),
                        customer.getName()
                ),
                payment.getReceivedDate(),
                payment.getAmount(),
                payment.getMethod(),
                payment.getReferenceNumber(),
                userRef(payment.getCreatedBy()),
                payment.getVoidedAt() == null ? null : new PaymentDetailResponse.Voidance(
                        payment.getVoidReason(),
                        userRef(payment.getVoidedBy()),
                        payment.getVoidedAt()
                ),
                payment.getCreatedAt(),
                payment.getVersion(),
                paymentAllocationRepository.findAllByPaymentIdOrderByIdAsc(payment.getId())
                        .stream()
                        .map(allocation -> new PaymentDetailResponse.Allocation(
                                allocation.getReceivable().getId(),
                                allocation.getReceivable().getReceivableNumber(),
                                allocation.getReceivable().getDoNumber(),
                                allocation.getAllocatedAmount()
                        ))
                        .toList()
        );
    }

    private PaymentDetailResponse.UserRef userRef(Long userId) {
        return new PaymentDetailResponse.UserRef(userId, userDirectoryService.findName(userId));
    }
}
