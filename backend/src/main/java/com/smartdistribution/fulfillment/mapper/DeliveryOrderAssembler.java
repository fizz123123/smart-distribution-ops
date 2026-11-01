package com.smartdistribution.fulfillment.mapper;

import com.smartdistribution.fulfillment.dto.response.DeliveryOrderDetailResponse;
import com.smartdistribution.fulfillment.dto.response.DeliveryOrderSummaryResponse;
import com.smartdistribution.fulfillment.entity.DeliveryOrder;
import com.smartdistribution.fulfillment.entity.DeliveryOrderLine;
import com.smartdistribution.fulfillment.repository.DeliveryOrderLineRepository;
import com.smartdistribution.fulfillment.service.DeliveryOrderMasterData;
import com.smartdistribution.identity.service.UserDirectoryService;
import com.smartdistribution.masterdata.companyitem.entity.CompanyItem;
import com.smartdistribution.masterdata.customer.entity.Customer;
import com.smartdistribution.sales.service.SalesOrderDeliveryService;
import com.smartdistribution.sales.service.SalesOrderForDelivery;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 組出 DO 的回應，需要查 SO、客戶、料號與使用者，所以不是單純的欄位轉換；回應都不含價格
 */
@Component
@RequiredArgsConstructor
public class DeliveryOrderAssembler {

    private final DeliveryOrderLineRepository deliveryOrderLineRepository;
    private final DeliveryOrderMasterData deliveryOrderMasterData;
    private final SalesOrderDeliveryService salesOrderDeliveryService;
    private final UserDirectoryService userDirectoryService;

    //清單頁一次查好這一頁的 SO、客戶與建立者姓名
    public Page<DeliveryOrderSummaryResponse> toSummaries(Page<DeliveryOrder> deliveryOrders) {
        Map<Long, SalesOrderForDelivery> salesOrders = salesOrderDeliveryService.findForDelivery(
                deliveryOrders.stream().map(DeliveryOrder::getSalesOrderId).collect(Collectors.toSet())
        );
        Map<Long, Customer> customers = deliveryOrderMasterData.findCustomers(
                salesOrders.values().stream().map(SalesOrderForDelivery::customerId).collect(Collectors.toSet())
        );

        Set<Long> userIds = new HashSet<>();
        deliveryOrders.forEach(deliveryOrder -> userIds.add(deliveryOrder.getCreatedBy()));
        Map<Long, String> userNames = userDirectoryService.findNamesByIds(userIds);

        return deliveryOrders.map(deliveryOrder -> {
            SalesOrderForDelivery salesOrder = salesOrders.get(deliveryOrder.getSalesOrderId());
            Customer customer = salesOrder == null ? null : customers.get(salesOrder.customerId());

            return new DeliveryOrderSummaryResponse(
                    deliveryOrder.getId(),
                    deliveryOrder.getDoNumber(),
                    deliveryOrder.getStatus(),
                    deliveryOrder.getSalesOrderId(),
                    salesOrder == null ? null : salesOrder.soNumber(),
                    customer == null ? null : customer.getCustomerCode(),
                    customer == null ? null : customer.getName(),
                    deliveryOrder.getPlannedShipmentDate(),
                    userNames.get(deliveryOrder.getCreatedBy()),
                    deliveryOrder.getCreatedAt()
            );
        });
    }

    public DeliveryOrderDetailResponse toDetail(DeliveryOrder deliveryOrder) {
        SalesOrderForDelivery salesOrder = salesOrderDeliveryService
                .findForDelivery(List.of(deliveryOrder.getSalesOrderId()))
                .get(deliveryOrder.getSalesOrderId());
        Customer customer = deliveryOrderMasterData.findCustomers(List.of(salesOrder.customerId()))
                .get(salesOrder.customerId());

        List<DeliveryOrderLine> lines =
                deliveryOrderLineRepository.findAllByDeliveryOrderIdOrderByLineNoAsc(deliveryOrder.getId());
        Map<Long, CompanyItem> items = deliveryOrderMasterData.findCompanyItems(
                lines.stream().map(DeliveryOrderLine::getCompanyItemId).collect(Collectors.toSet())
        );
        Map<Long, SalesOrderForDelivery.Line> salesOrderLines = salesOrder.lines()
                .stream()
                .collect(Collectors.toMap(SalesOrderForDelivery.Line::id, Function.identity()));

        return new DeliveryOrderDetailResponse(
                deliveryOrder.getId(),
                deliveryOrder.getDoNumber(),
                deliveryOrder.getStatus(),
                new DeliveryOrderDetailResponse.SalesOrderRef(
                        salesOrder.id(),
                        salesOrder.soNumber(),
                        salesOrder.customerOrderNumber()
                ),
                new DeliveryOrderDetailResponse.CustomerRef(
                        customer.getId(),
                        customer.getCustomerCode(),
                        customer.getName()
                ),
                new DeliveryOrderDetailResponse.Delivery(
                        salesOrder.deliveryMethod(),
                        salesOrder.deliveryAddress(),
                        salesOrder.contactName(),
                        salesOrder.contactPhone()
                ),
                deliveryOrder.getPlannedShipmentDate(),
                deliveryOrder.getRemark(),
                userRef(deliveryOrder.getCreatedBy()),
                deliveryOrder.getVoidedAt() == null ? null : new DeliveryOrderDetailResponse.Voidance(
                        deliveryOrder.getVoidReason(),
                        userRef(deliveryOrder.getVoidedBy()),
                        deliveryOrder.getVoidedAt()
                ),
                deliveryOrder.getCreatedAt(),
                deliveryOrder.getUpdatedAt(),
                deliveryOrder.getVersion(),
                lines.stream()
                        .map(line -> toLine(line, items.get(line.getCompanyItemId()), salesOrderLines.get(line.getSalesOrderLineId())))
                        .toList()
        );
    }

    private DeliveryOrderDetailResponse.Line toLine(
            DeliveryOrderLine line,
            CompanyItem item,
            SalesOrderForDelivery.Line salesOrderLine
    ) {
        return new DeliveryOrderDetailResponse.Line(
                line.getId(),
                line.getLineNo(),
                line.getSalesOrderLineId(),
                salesOrderLine.lineNo(),
                line.getCompanyItemId(),
                item.getItemCode(),
                item.getName(),
                salesOrderLine.customerItemCode(),
                line.getUnit(),
                line.getPlannedQuantity(),
                line.getPlannedBaseQty(),
                line.getActualQuantity(),
                line.getActualBaseQty()
        );
    }

    private DeliveryOrderDetailResponse.UserRef userRef(Long userId) {
        return new DeliveryOrderDetailResponse.UserRef(userId, userDirectoryService.findName(userId));
    }
}
