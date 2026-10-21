package com.smartdistribution.inventory.controller;

import com.smartdistribution.inventory.dto.request.UpdateInventoryPolicyRequest;
import com.smartdistribution.inventory.dto.response.InventoryPolicyResponse;
import com.smartdistribution.inventory.service.InventoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 路徑掛在公司料號底下（12 號文件），但資料屬於庫存模組，所以 controller 放在這裡。
 */
@RestController
@RequestMapping("/api/v1/company-items/{companyItemId}/inventory-policy")
@RequiredArgsConstructor
public class InventoryPolicyController {

    private final InventoryService inventoryService;

    @GetMapping
    public ResponseEntity<InventoryPolicyResponse> findInventoryPolicy(
            @PathVariable Long companyItemId
    ) {
        InventoryPolicyResponse response = inventoryService.findInventoryPolicy(companyItemId);

        return ResponseEntity.ok(response);
    }

    @PutMapping
    public ResponseEntity<InventoryPolicyResponse> updateInventoryPolicy(
            @PathVariable Long companyItemId,
            @Valid @RequestBody UpdateInventoryPolicyRequest request
    ) {
        InventoryPolicyResponse response =
                inventoryService.updateInventoryPolicy(companyItemId, request);

        return ResponseEntity.ok(response);
    }
}
