package com.smartdistribution.inventory.controller;

import com.smartdistribution.inventory.dto.response.InventoryBalanceResponse;
import com.smartdistribution.inventory.dto.response.InventoryConsistencyResponse;
import com.smartdistribution.inventory.dto.response.InventoryReservationResponse;
import com.smartdistribution.inventory.dto.response.InventoryTransactionResponse;
import com.smartdistribution.inventory.service.InventoryQueryService;
import com.smartdistribution.shared.web.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryQueryService inventoryQueryService;

    //排序固定為料號，寫在查詢裡
    @GetMapping("/balances")
    public ResponseEntity<PageResponse<InventoryBalanceResponse>> findBalances(
            @RequestParam(required = false) String keyword,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(inventoryQueryService.findBalances(keyword, pageable));
    }

    @GetMapping("/items/{companyItemId}/transactions")
    public ResponseEntity<PageResponse<InventoryTransactionResponse>> findTransactions(
            @PathVariable Long companyItemId,
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC)
            Pageable pageable
    ) {
        return ResponseEntity.ok(inventoryQueryService.findTransactions(companyItemId, pageable));
    }

    @GetMapping("/items/{companyItemId}/reservations")
    public ResponseEntity<List<InventoryReservationResponse>> findReservations(
            @PathVariable Long companyItemId
    ) {
        return ResponseEntity.ok(inventoryQueryService.findReservations(companyItemId));
    }

    @GetMapping("/consistency-check")
    public ResponseEntity<InventoryConsistencyResponse> checkConsistency() {
        return ResponseEntity.ok(inventoryQueryService.checkConsistency());
    }
}
