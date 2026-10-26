package com.smartdistribution.inventory.service;

import com.smartdistribution.inventory.dto.response.InventoryBalanceResponse;
import com.smartdistribution.inventory.dto.response.InventoryConsistencyResponse;
import com.smartdistribution.inventory.dto.response.InventoryReservationResponse;
import com.smartdistribution.inventory.dto.response.InventoryTransactionResponse;
import com.smartdistribution.shared.web.response.PageResponse;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface InventoryQueryService {

    PageResponse<InventoryBalanceResponse> findBalances(String keyword, Pageable pageable);

    PageResponse<InventoryTransactionResponse> findTransactions(Long companyItemId, Pageable pageable);

    List<InventoryReservationResponse> findReservations(Long companyItemId);

    /**
     * 由異動與預留事件重新驗算目前值，回傳不一致的項目
     */
    InventoryConsistencyResponse checkConsistency();
}
