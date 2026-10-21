package com.smartdistribution.masterdata.supplier.mapper;

import com.smartdistribution.masterdata.supplier.dto.response.SupplierResponse;
import com.smartdistribution.masterdata.supplier.entity.Supplier;
import org.springframework.stereotype.Component;

@Component
public class SupplierMapper {

    public SupplierResponse toResponse(Supplier supplier) {
        return new SupplierResponse(
                supplier.getId(),
                supplier.getSupplierCode(),
                supplier.getName(),
                supplier.getTaxId(),
                supplier.getContactName(),
                supplier.getPhone(),
                supplier.getEmail(),
                supplier.getAddress(),
                supplier.getStatus(),
                supplier.getCreatedBy(),
                supplier.getCreatedAt(),
                supplier.getUpdatedAt(),
                supplier.getVersion()
        );
    }
}
