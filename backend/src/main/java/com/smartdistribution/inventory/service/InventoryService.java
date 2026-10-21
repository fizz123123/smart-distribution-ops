package com.smartdistribution.inventory.service;

import com.smartdistribution.inventory.dto.request.UpdateInventoryPolicyRequest;
import com.smartdistribution.inventory.dto.response.InventoryPolicyResponse;

public interface InventoryService {

    /**
     * 公司料號建立時呼叫，在同一個交易中建立安全庫存政策與庫存餘額列。
     */
    void initializeForCompanyItem(Long companyItemId);

    InventoryPolicyResponse findInventoryPolicy(Long companyItemId);

    InventoryPolicyResponse updateInventoryPolicy(
            Long companyItemId,
            UpdateInventoryPolicyRequest request
    );
}
