package com.smartdistribution.inventory.service.impl;

import com.smartdistribution.inventory.dto.response.InventoryBalanceResponse;
import com.smartdistribution.inventory.dto.response.InventoryConsistencyResponse;
import com.smartdistribution.inventory.dto.response.InventoryConsistencyResponse.Issue;
import com.smartdistribution.inventory.dto.response.InventoryConsistencyResponse.IssueType;
import com.smartdistribution.inventory.dto.response.InventoryReservationResponse;
import com.smartdistribution.inventory.dto.response.InventoryTransactionResponse;
import com.smartdistribution.inventory.entity.InventoryBalance;
import com.smartdistribution.inventory.entity.InventoryReservation;
import com.smartdistribution.inventory.entity.InventoryReservationEvent;
import com.smartdistribution.inventory.entity.ReservationEventType;
import com.smartdistribution.inventory.repository.InventoryBalanceRepository;
import com.smartdistribution.inventory.repository.InventoryBalanceRow;
import com.smartdistribution.inventory.repository.InventoryReservationEventRepository;
import com.smartdistribution.inventory.repository.InventoryReservationRepository;
import com.smartdistribution.inventory.repository.InventoryTransactionRepository;
import com.smartdistribution.inventory.repository.QuantityByKey;
import com.smartdistribution.inventory.service.InventoryQueryService;
import com.smartdistribution.shared.exception.BusinessException;
import com.smartdistribution.shared.exception.ErrorCode;
import com.smartdistribution.shared.web.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InventoryQueryServiceImpl implements InventoryQueryService {

    private final InventoryBalanceRepository inventoryBalanceRepository;
    private final InventoryTransactionRepository inventoryTransactionRepository;
    private final InventoryReservationRepository inventoryReservationRepository;
    private final InventoryReservationEventRepository inventoryReservationEventRepository;

    @Override
    @PreAuthorize("hasAuthority('INVENTORY_VIEW')")
    public PageResponse<InventoryBalanceResponse> findBalances(String keyword, Pageable pageable) {
        String normalizedKeyword = keyword == null || keyword.isBlank() ? null : keyword.strip();
        Page<InventoryBalanceRow> rows = inventoryBalanceRepository.searchBalances(normalizedKeyword, pageable);

        List<Long> companyItemIds = rows.map(InventoryBalanceRow::companyItemId).getContent();
        Map<Long, BigDecimal> reservedQtyByItem = companyItemIds.isEmpty()
                ? Map.of()
                : toMap(inventoryReservationRepository.sumOpenQtyByCompanyItemIds(companyItemIds));

        return PageResponse.from(rows.map(row -> {
            BigDecimal reservedQty = reservedQtyByItem.getOrDefault(row.companyItemId(), BigDecimal.ZERO);

            return new InventoryBalanceResponse(
                    row.companyItemId(),
                    row.itemCode(),
                    row.name(),
                    row.baseUnit(),
                    row.onHandQty(),
                    reservedQty,
                    row.onHandQty().subtract(reservedQty),
                    row.safetyStockBaseQty()
            );
        }));
    }

    @Override
    @PreAuthorize("hasAuthority('INVENTORY_TRANSACTION_VIEW')")
    public PageResponse<InventoryTransactionResponse> findTransactions(Long companyItemId, Pageable pageable) {
        ensureItemExists(companyItemId);

        Page<InventoryTransactionResponse> transactions = inventoryTransactionRepository
                .findAllByCompanyItemId(companyItemId, pageable)
                .map(transaction -> new InventoryTransactionResponse(
                        transaction.getId(),
                        transaction.getTransactionType(),
                        transaction.getQuantityChange(),
                        transaction.getSourceId(),
                        transaction.getActorId(),
                        transaction.getOccurredAt()
                ));

        return PageResponse.from(transactions);
    }

    @Override
    @PreAuthorize("hasAuthority('INVENTORY_RESERVATION_VIEW')")
    public List<InventoryReservationResponse> findReservations(Long companyItemId) {
        ensureItemExists(companyItemId);

        List<InventoryReservation> reservations =
                inventoryReservationRepository.findAllByCompanyItemIdOrderByIdAsc(companyItemId);
        if (reservations.isEmpty()) {
            return List.of();
        }

        Map<Long, List<InventoryReservationResponse.Event>> eventsByReservation =
                inventoryReservationEventRepository
                        .findAllByReservationIdInOrderByIdAsc(
                                reservations.stream().map(InventoryReservation::getId).toList()
                        )
                        .stream()
                        .collect(Collectors.groupingBy(
                                event -> event.getReservation().getId(),
                                Collectors.mapping(this::toEvent, Collectors.toList())
                        ));

        return reservations.stream()
                .map(reservation -> new InventoryReservationResponse(
                        reservation.getId(),
                        reservation.getSalesOrderId(),
                        reservation.getSalesOrderNumber(),
                        reservation.getSalesOrderLineId(),
                        reservation.getOpenQty(),
                        eventsByReservation.getOrDefault(reservation.getId(), List.of())
                ))
                .toList();
    }

    @Override
    @PreAuthorize("hasAuthority('INVENTORY_VIEW')")
    public InventoryConsistencyResponse checkConsistency() {
        List<InventoryBalance> balances = inventoryBalanceRepository.findAll();
        Map<Long, BigDecimal> transactionSumByItem = toMap(inventoryTransactionRepository.sumQuantityByCompanyItem());
        Map<Long, BigDecimal> reservedQtyByItem = toMap(inventoryReservationRepository.sumOpenQtyByCompanyItem());
        Map<Long, BigDecimal> eventSumByReservation = toMap(
                inventoryReservationEventRepository.sumNetQuantityByReservation(ReservationEventType.RESERVE)
        );

        List<Issue> issues = new ArrayList<>();

        // 1. 可出貨實體數量 ＝ 異動加總；2. 有效預留總量 ≤ 可出貨實體數量
        for (InventoryBalance balance : balances) {
            Long companyItemId = balance.getCompanyItemId();
            BigDecimal transactionSum = transactionSumByItem.getOrDefault(companyItemId, BigDecimal.ZERO);
            BigDecimal reservedQty = reservedQtyByItem.getOrDefault(companyItemId, BigDecimal.ZERO);

            if (transactionSum.compareTo(balance.getOnHandQty()) != 0) {
                issues.add(new Issue(companyItemId, null, IssueType.ON_HAND_MISMATCH,
                        transactionSum, balance.getOnHandQty()));
            }

            if (reservedQty.compareTo(balance.getOnHandQty()) > 0) {
                issues.add(new Issue(companyItemId, null, IssueType.OVER_RESERVED,
                        balance.getOnHandQty(), reservedQty));
            }
        }

        // 3. 每筆有效預留 ＝ RESERVE － CONSUME － RELEASE
        for (InventoryReservation reservation : inventoryReservationRepository.findAll()) {
            BigDecimal eventSum = eventSumByReservation.getOrDefault(reservation.getId(), BigDecimal.ZERO);

            if (eventSum.compareTo(reservation.getOpenQty()) != 0) {
                issues.add(new Issue(reservation.getCompanyItemId(), reservation.getId(),
                        IssueType.RESERVATION_MISMATCH, eventSum, reservation.getOpenQty()));
            }
        }

        return new InventoryConsistencyResponse(Instant.now(), balances.size(), issues);
    }

    // 每個公司料號都有餘額列，找不到就代表料號不存在
    private void ensureItemExists(Long companyItemId) {
        if (inventoryBalanceRepository.findByCompanyItemId(companyItemId).isEmpty()) {
            throw new BusinessException(ErrorCode.COMPANY_ITEM_NOT_FOUND);
        }
    }

    private InventoryReservationResponse.Event toEvent(InventoryReservationEvent event) {
        return new InventoryReservationResponse.Event(
                event.getId(),
                event.getEventType(),
                event.getQuantity(),
                event.getSourceType(),
                event.getSourceId(),
                event.getActorId(),
                event.getOccurredAt()
        );
    }

    private Map<Long, BigDecimal> toMap(List<QuantityByKey> rows) {
        return rows.stream().collect(Collectors.toMap(QuantityByKey::key, QuantityByKey::quantity));
    }
}
