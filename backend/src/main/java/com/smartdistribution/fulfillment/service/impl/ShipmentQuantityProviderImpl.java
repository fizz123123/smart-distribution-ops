package com.smartdistribution.fulfillment.service.impl;

import com.smartdistribution.fulfillment.service.DeliveryOrderQuantities;
import com.smartdistribution.sales.service.ShipmentQuantityProvider.ShipmentQuantity;
import com.smartdistribution.sales.service.ShipmentQuantityProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Map;

/**
 * 銷售模組定義、出貨模組實作：提供 SO 明細的已出貨量與進行中 DO 數量
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ShipmentQuantityProviderImpl implements ShipmentQuantityProvider {

    private final DeliveryOrderQuantities deliveryOrderQuantities;

    @Override
    public Map<Long, ShipmentQuantity> findBySalesOrderLines(Collection<Long> salesOrderLineIds) {
        return deliveryOrderQuantities.findBySalesOrderLines(salesOrderLineIds);
    }
}
