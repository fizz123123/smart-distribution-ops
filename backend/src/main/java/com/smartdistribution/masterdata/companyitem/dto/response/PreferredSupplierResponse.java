package com.smartdistribution.masterdata.companyitem.dto.response;

import com.smartdistribution.masterdata.common.MasterDataStatus;

public record PreferredSupplierResponse(
        Long id,
        String supplierCode,
        String name,
        MasterDataStatus status
) {
}
