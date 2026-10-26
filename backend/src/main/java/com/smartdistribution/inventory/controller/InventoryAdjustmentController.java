package com.smartdistribution.inventory.controller;

import com.smartdistribution.inventory.dto.request.PostInventoryAdjustmentRequest;
import com.smartdistribution.inventory.dto.request.ReverseInventoryAdjustmentRequest;
import com.smartdistribution.inventory.dto.response.InventoryAdjustmentResponse;
import com.smartdistribution.inventory.service.InventoryAdjustmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/inventory/adjustments")
@RequiredArgsConstructor
public class InventoryAdjustmentController {

    private final InventoryAdjustmentService inventoryAdjustmentService;

    @GetMapping("/{adjustmentId}")
    public ResponseEntity<InventoryAdjustmentResponse> findAdjustment(@PathVariable Long adjustmentId) {
        return ResponseEntity.ok(inventoryAdjustmentService.findAdjustment(adjustmentId));
    }

    @PostMapping
    public ResponseEntity<InventoryAdjustmentResponse> postAdjustment(
            @Valid @RequestBody PostInventoryAdjustmentRequest request
    ) {
        InventoryAdjustmentResponse response = inventoryAdjustmentService.postAdjustment(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    //回傳新建立的反向調整
    @PostMapping("/{adjustmentId}/reverse")
    public ResponseEntity<InventoryAdjustmentResponse> reverseAdjustment(
            @PathVariable Long adjustmentId,
            @Valid @RequestBody ReverseInventoryAdjustmentRequest request
    ) {
        InventoryAdjustmentResponse response =
                inventoryAdjustmentService.reverseAdjustment(adjustmentId, request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
