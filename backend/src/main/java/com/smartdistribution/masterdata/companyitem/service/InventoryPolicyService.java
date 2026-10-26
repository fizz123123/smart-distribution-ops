package com.smartdistribution.masterdata.companyitem.service;

import com.smartdistribution.masterdata.companyitem.dto.request.UpdateInventoryPolicyRequest;
import com.smartdistribution.masterdata.companyitem.dto.response.InventoryPolicyResponse;

public interface InventoryPolicyService {

    InventoryPolicyResponse findInventoryPolicy(Long companyItemId);

    InventoryPolicyResponse updateInventoryPolicy(
            Long companyItemId,
            UpdateInventoryPolicyRequest request
    );
}
