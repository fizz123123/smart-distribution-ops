package com.smartdistribution.masterdata.companyitem.controller;

import com.smartdistribution.masterdata.companyitem.dto.request.UpdateInventoryPolicyRequest;
import com.smartdistribution.masterdata.companyitem.dto.response.InventoryPolicyResponse;
import com.smartdistribution.masterdata.companyitem.service.InventoryPolicyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/company-items/{companyItemId}/inventory-policy")
@RequiredArgsConstructor
public class InventoryPolicyController {

    private final InventoryPolicyService inventoryPolicyService;

    @GetMapping
    public ResponseEntity<InventoryPolicyResponse> findInventoryPolicy(
            @PathVariable Long companyItemId
    ) {
        InventoryPolicyResponse response = inventoryPolicyService.findInventoryPolicy(companyItemId);

        return ResponseEntity.ok(response);
    }

    @PutMapping
    public ResponseEntity<InventoryPolicyResponse> updateInventoryPolicy(
            @PathVariable Long companyItemId,
            @Valid @RequestBody UpdateInventoryPolicyRequest request
    ) {
        InventoryPolicyResponse response =
                inventoryPolicyService.updateInventoryPolicy(companyItemId, request);

        return ResponseEntity.ok(response);
    }
}
