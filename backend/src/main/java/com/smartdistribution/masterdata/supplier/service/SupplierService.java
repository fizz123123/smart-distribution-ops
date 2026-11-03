package com.smartdistribution.masterdata.supplier.service;

import com.smartdistribution.masterdata.common.MasterDataStatus;
import com.smartdistribution.masterdata.supplier.dto.request.CreateSupplierRequest;
import com.smartdistribution.masterdata.supplier.dto.request.UpdateSupplierRequest;
import com.smartdistribution.masterdata.supplier.dto.response.SupplierResponse;
import com.smartdistribution.shared.web.request.ReasonRequest;
import com.smartdistribution.shared.web.request.VersionRequest;
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

    SupplierResponse submitSupplier(Long supplierId, VersionRequest request);

    SupplierResponse deactivateSupplier(Long supplierId, ReasonRequest request);

    SupplierResponse activateSupplier(Long supplierId, ReasonRequest request);
}
