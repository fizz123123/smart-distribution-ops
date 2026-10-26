package com.smartdistribution.inventory.service.impl;

import com.smartdistribution.inventory.entity.InventoryBalance;
import com.smartdistribution.inventory.entity.InventoryReservation;
import com.smartdistribution.inventory.entity.InventoryReservationEvent;
import com.smartdistribution.inventory.entity.ReservationEventType;
import com.smartdistribution.inventory.entity.ReservationSourceType;
import com.smartdistribution.inventory.repository.InventoryBalanceRepository;
import com.smartdistribution.inventory.repository.InventoryReservationEventRepository;
import com.smartdistribution.inventory.repository.InventoryReservationRepository;
import com.smartdistribution.inventory.repository.QuantityByKeyProjection;
import com.smartdistribution.inventory.service.InventoryBalanceLocks;
import com.smartdistribution.inventory.service.InventoryService;
import com.smartdistribution.inventory.service.ReservationDemand;
import com.smartdistribution.inventory.service.ReservationLine;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InventoryServiceImpl implements InventoryService {

    private final InventoryBalanceRepository inventoryBalanceRepository;
    private final InventoryReservationRepository inventoryReservationRepository;
    private final InventoryReservationEventRepository inventoryReservationEventRepository;
    private final InventoryBalanceLocks inventoryBalanceLocks;

    @Override
    @Transactional
    public void initializeForCompanyItem(Long companyItemId) {
        inventoryBalanceRepository.save(new InventoryBalance(companyItemId));
    }

    @Override
    @Transactional
    public void reserveForSalesOrder(
            List<ReservationLine> lines,
            Long approvalRequestId,
            Long actorId
    ) {
        //1.依料號 ID 由小到大鎖定
        //2.鎖定後才計算可用量，不使用畫面上的舊數字
        Map<Long, InventoryBalance> balances = inventoryBalanceLocks.lockInItemOrder(
                lines.stream().map(ReservationLine::companyItemId).toList()
        );

        Map<Long, BigDecimal> availableByItem = new HashMap<>();
        balances.forEach((companyItemId, balance) ->
                availableByItem.put(companyItemId, inventoryBalanceLocks.availableQty(balance)));

        Instant now = Instant.now();

        for (ReservationLine line : lines) {
            BigDecimal available = availableByItem.get(line.companyItemId());

            //可用量不足時只預留可用的部分，不得超額預留；資料不一致造成負數時視為 0
            BigDecimal reservedQty = available.min(line.baseQty()).max(BigDecimal.ZERO);
            availableByItem.put(line.companyItemId(), available.subtract(reservedQty));

            //每筆明細都建立預留列，預留量 0 也建，之後 PO 入庫補足時直接累加
            InventoryReservation reservation = new InventoryReservation(
                    line.salesOrderLineId(),
                    line.salesOrderId(),
                    line.salesOrderNumber(),
                    line.companyItemId()
            );

            reservation.increase(reservedQty);
            inventoryReservationRepository.save(reservation);

            //事件數量必須大於 0，所以沒有預留到的明細不寫事件
            if (reservedQty.signum() > 0) {
                inventoryReservationEventRepository.save(new InventoryReservationEvent(
                        reservation,
                        ReservationEventType.RESERVE,
                        reservedQty,
                        ReservationSourceType.SO_APPROVAL,
                        approvalRequestId,
                        actorId,
                        now
                ));
            }
        }
    }

    @Override
    @Transactional
    public void releaseForCancellation(
            List<ReservationDemand> demands,
            Long cancellationId,
            Long actorId
    ) {
        // 釋放預留也要先鎖定，避免和同一料號的預留或調整同時計算
        inventoryBalanceLocks.lockInItemOrder(demands.stream().map(ReservationDemand::companyItemId).toList());

        Map<Long, InventoryReservation> reservations = inventoryReservationRepository
                .findAllBySalesOrderLineIdIn(demands.stream().map(ReservationDemand::salesOrderLineId).toList())
                .stream()
                .collect(Collectors.toMap(InventoryReservation::getSalesOrderLineId, reservation -> reservation));

        Instant now = Instant.now();

        for (ReservationDemand demand : demands) {
            InventoryReservation reservation = reservations.get(demand.salesOrderLineId());

            // 例：訂 5、預留 2、取消 3 → 剩餘需求 2，預留沒有超過需求，不釋放
            //     訂 5、預留 2、取消 4 → 剩餘需求 1，釋放 1
            BigDecimal releasedQty = reservation == null
                    ? BigDecimal.ZERO
                    : reservation.getOpenQty().subtract(demand.remainingQty()).max(BigDecimal.ZERO);

            if (releasedQty.signum() > 0) {
                reservation.decrease(releasedQty);
                inventoryReservationEventRepository.save(new InventoryReservationEvent(
                        reservation,
                        ReservationEventType.RELEASE,
                        releasedQty,
                        ReservationSourceType.SO_CANCELLATION,
                        cancellationId,
                        actorId,
                        now
                ));
            }
        }
    }

    @Override
    public Map<Long, BigDecimal> findOpenQtyBySalesOrderLines(Collection<Long> salesOrderLineIds) {
        if (salesOrderLineIds.isEmpty()) {
            return Map.of();
        }

        return inventoryReservationRepository.findAllBySalesOrderLineIdIn(salesOrderLineIds)
                .stream()
                .collect(Collectors.toMap(
                        InventoryReservation::getSalesOrderLineId,
                        InventoryReservation::getOpenQty
                ));
    }

    @Override
    public Map<Long, BigDecimal> findAvailableQty(Collection<Long> companyItemIds) {
        if (companyItemIds.isEmpty()) {
            return Map.of();
        }

        List<Long> ids = List.copyOf(companyItemIds);
        Map<Long, BigDecimal> reservedByItem = inventoryReservationRepository
                .sumOpenQtyByCompanyItemIds(ids)
                .stream()
                .collect(Collectors.toMap(QuantityByKeyProjection::key, QuantityByKeyProjection::quantity));

        return inventoryBalanceRepository.findAllByCompanyItemIdIn(ids)
                .stream()
                .collect(Collectors.toMap(
                        InventoryBalance::getCompanyItemId,
                        balance -> balance.getOnHandQty().subtract(
                                reservedByItem.getOrDefault(balance.getCompanyItemId(), BigDecimal.ZERO)
                        )
                ));
    }
}
