package com.smartdistribution.masterdata.supplier.service;

import com.smartdistribution.masterdata.common.MasterDataStatus;
import com.smartdistribution.masterdata.supplier.dto.request.ChangeSupplierStatusRequest;
import com.smartdistribution.masterdata.supplier.dto.request.CreateSupplierRequest;
import com.smartdistribution.masterdata.supplier.dto.request.SubmitSupplierRequest;
import com.smartdistribution.masterdata.supplier.dto.request.UpdateSupplierRequest;
import com.smartdistribution.masterdata.supplier.dto.response.SupplierResponse;
import com.smartdistribution.shared.web.response.PageResponse;
import org.springframework.data.domain.Pageable;

public interface SupplierService {

    PageResponse<SupplierResponse> findSuppliers(
            String keyword,
            MasterDataStatus status,
            Pageable pageable
    );

    SupplierResponse findSupplier(Long supplierId);

    SupplierResponse createSupplier(CreateSupplierRequest request);

    SupplierResponse updateSupplier(Long supplierId, UpdateSupplierRequest request);

    SupplierResponse submitSupplier(Long supplierId, SubmitSupplierRequest request);

    SupplierResponse deactivateSupplier(Long supplierId, ChangeSupplierStatusRequest request);

    SupplierResponse activateSupplier(Long supplierId, ChangeSupplierStatusRequest request);
}
