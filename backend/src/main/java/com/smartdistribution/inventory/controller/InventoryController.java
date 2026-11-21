package com.smartdistribution.inventory.controller;

import com.smartdistribution.inventory.dto.response.InventoryBalanceResponse;
import com.smartdistribution.inventory.dto.response.InventoryConsistencyResponse;
import com.smartdistribution.inventory.dto.response.InventoryReservationResponse;
import com.smartdistribution.inventory.dto.response.InventoryTransactionResponse;
import com.smartdistribution.inventory.service.InventoryQueryService;
import com.smartdistribution.shared.web.response.PageResponse;
import com.smartdistribution.shared.web.sort.SortWhitelist;
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

    //庫存餘額：預設依料號；可用量與是否低於安全庫存在查詢裡算出來，用運算式排序
    //料號、品名與安全庫存在主檔模組，餘額查詢原本就要 join 才能顯示，排序沿用同一個 join，沒有新增依賴
    //balance、item、policy 是 InventoryBalanceRepository.searchBalances 的別名，改名要一起改
    private static final SortWhitelist BALANCE_SORTS = SortWhitelist.builder()
            .field("itemCode", "item.itemCode")
            .field("name", "item.name")
            .field("onHandQty", "balance.onHandQty")
            .expression("availableQty", "balance.onHandQty - balance.reservedQty")
            .expression(
                    "belowSafetyStock",
                    "CASE WHEN balance.onHandQty - balance.reservedQty < policy.safetyStockBaseQty THEN 1 ELSE 0 END"
            )
            .defaultSort(Sort.Direction.ASC, "itemCode")
            .build();

    //庫存異動：預設依發生時間，新的在前
    private static final SortWhitelist TRANSACTION_SORTS = SortWhitelist.builder()
            .field("occurredAt", "occurredAt")
            .field("transactionType", "transactionType")
            .defaultSort(Sort.Direction.DESC, "occurredAt")
            .build();

    private final InventoryQueryService inventoryQueryService;

    @GetMapping("/balances")
    public ResponseEntity<PageResponse<InventoryBalanceResponse>> findBalances(
            @RequestParam(required = false) String keyword,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(inventoryQueryService.findBalances(keyword, BALANCE_SORTS.apply(pageable)));
    }

    @GetMapping("/items/{companyItemId}/transactions")
    public ResponseEntity<PageResponse<InventoryTransactionResponse>> findTransactions(
            @PathVariable Long companyItemId,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(inventoryQueryService.findTransactions(
                companyItemId, TRANSACTION_SORTS.apply(pageable)
        ));
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
