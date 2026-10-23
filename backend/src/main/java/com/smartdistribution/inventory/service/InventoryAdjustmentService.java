package com.smartdistribution.inventory.service;

import com.smartdistribution.inventory.dto.request.PostInventoryAdjustmentRequest;
import com.smartdistribution.inventory.dto.request.ReverseInventoryAdjustmentRequest;
import com.smartdistribution.inventory.dto.response.InventoryAdjustmentResponse;

public interface InventoryAdjustmentService {

    InventoryAdjustmentResponse findAdjustment(Long adjustmentId);

    /**
     * 建立並直接過帳庫存調整，期初庫存也使用這個方法（D-025）。
     */
    InventoryAdjustmentResponse postAdjustment(PostInventoryAdjustmentRequest request);

    /**
     * 建立一張相反方向的調整，並把原調整標記為 REVERSED（D-010）。
     */
    InventoryAdjustmentResponse reverseAdjustment(
            Long adjustmentId,
            ReverseInventoryAdjustmentRequest request
    );
}
