package com.smartdistribution.masterdata.customer.dto.response;

import com.smartdistribution.masterdata.common.MasterDataStatus;
import com.smartdistribution.masterdata.customer.entity.PaymentTerm;

import java.time.Instant;

public record CustomerResponse(
        Long id,
        String customerCode,
        String name,
        String taxId,
        String contactName,
        String phone,
        String email,
        String address,
        PaymentTerm paymentTerm,
        MasterDataStatus status,
        Long createdBy,
        Instant createdAt,
        Instant updatedAt,
        Long version
) {
}
