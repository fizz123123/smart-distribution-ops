package com.smartdistribution.fulfillment;

import com.smartdistribution.masterdata.common.UnitOfMeasure;
import com.smartdistribution.masterdata.companyitem.entity.CompanyItem;
import com.smartdistribution.masterdata.customer.entity.Customer;
import com.smartdistribution.masterdata.customer.entity.PaymentTerm;
import com.smartdistribution.masterdata.customeritemmapping.entity.CustomerItemMapping;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 通知出貨與 DO 草稿：只能用已預留、還沒排入其他 DO 的數量；撤回、作廢；排入出貨的數量不能取消（AT-040、AT-041）
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DeliveryOrderApiIntegrationTest extends DeliveryOrderTestSupport {

    @Test
    void onlyApprovedSalesOrderCanBeShipped() throws Exception {
        String sales = login(createUser("do-sales", "SALES"));
        Customer customer = createActiveCustomer(PaymentTerm.NET_30);
        CompanyItem item = createActiveItem(UnitOfMeasure.PCS);
        CustomerItemMapping mapping = createActiveMapping(customer, item);
        createActivePrice(mapping, UnitOfMeasure.PCS, "200");

        //送審了但還沒核准
        Long salesOrderId = createSubmittedOrder(sales, draftBody(customer, line(mapping, "5", UnitOfMeasure.PCS)));

        notifyShipment(sales, salesOrderId, doLine(findLineIds(salesOrderId).getFirst(), "1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DO_SALES_ORDER_NOT_APPROVED"));
    }

    @Test
    void draftsTogetherCannotExceedReservation() throws Exception {
        String warehouseManager = login(createUser("wh-manager", "WAREHOUSE_MANAGER"));
        String salesManager = login(createUser("sales-manager", "SALES_MANAGER"));
        ApprovedOrder order = createApprovedOrder(warehouseManager, salesManager, "3", "5");

        notifyShipment(order.salesToken(), order.salesOrderId(), doLine(order.salesOrderLineId(), "2"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.doNumber", startsWith("DO-")))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.lines[0].plannedBaseQty").value(2));

        //預留 3，已排入 2，只剩 1 能通知
        notifyShipment(order.salesToken(), order.salesOrderId(), doLine(order.salesOrderLineId(), "2"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DO_QUANTITY_EXCEEDS_RESERVATION"))
                .andExpect(jsonPath("$.message", containsString("最多還能通知 1")));

        notifyShipment(order.salesToken(), order.salesOrderId(), doLine(order.salesOrderLineId(), "1"))
                .andExpect(status().isCreated());

        //排入 DO 的數量仍在預留裡，尚待規劃不變
        getOrder(salesManager, order.salesOrderId())
                .andExpect(jsonPath("$.lines[0].supply.reservedQty").value(3))
                .andExpect(jsonPath("$.lines[0].supply.deliveringQty").value(3))
                .andExpect(jsonPath("$.lines[0].supply.unplannedQty").value(2));

        assertConsistent(warehouseManager, order.item());
    }

    @Test
    void salesCanWithdrawOnlyOwnDraftAndWarehouseVoidsWithReason() throws Exception {
        String warehouseManager = login(createUser("wh-manager", "WAREHOUSE_MANAGER"));
        String salesManager = login(createUser("sales-manager", "SALES_MANAGER"));
        String warehouse = login(createUser("warehouse", "WAREHOUSE"));
        String otherSales = login(createUser("other-sales", "SALES"));
        ApprovedOrder order = createApprovedOrder(warehouseManager, salesManager, "5", "5");

        Long first = createDeliveryOrder(order.salesToken(), order.salesOrderId(), doLine(order.salesOrderLineId(), "2"));
        Long version = deliveryOrderVersion(order.salesToken(), first);

        //別的業務不能撤回；倉管沒有通知出貨的權限，也不能撤回
        withdraw(otherSales, first, version)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("DO_WITHDRAW_FORBIDDEN"));
        withdraw(warehouse, first, version).andExpect(status().isForbidden());

        withdraw(order.salesToken(), first, version)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VOIDED"))
                .andExpect(jsonPath("$.voidance.reason").value("業務撤回出貨通知"));

        //撤回後不再占用
        getOrder(salesManager, order.salesOrderId())
                .andExpect(jsonPath("$.lines[0].supply.deliveringQty").value(0));

        Long second = createDeliveryOrder(order.salesToken(), order.salesOrderId(), doLine(order.salesOrderLineId(), "5"));
        Long secondVersion = deliveryOrderVersion(warehouse, second);

        //業務不能作廢；倉管作廢時原因必填、版本要對
        voidOrder(order.salesToken(), second, "客戶改期", secondVersion).andExpect(status().isForbidden());
        voidOrder(warehouse, second, " ", secondVersion)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        voidOrder(warehouse, second, "客戶改期", secondVersion - 1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));

        voidOrder(warehouse, second, "客戶改期", secondVersion)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VOIDED"))
                .andExpect(jsonPath("$.voidance.reason").value("客戶改期"));

        voidOrder(warehouse, second, "再作廢一次", deliveryOrderVersion(warehouse, second))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DO_STATUS_CONFLICT"));
    }

    @Test
    void linesMustBelongToTheSalesOrderAndWarehouseSeesNoPrices() throws Exception {
        String warehouseManager = login(createUser("wh-manager", "WAREHOUSE_MANAGER"));
        String salesManager = login(createUser("sales-manager", "SALES_MANAGER"));
        String warehouse = login(createUser("warehouse", "WAREHOUSE"));
        ApprovedOrder order = createApprovedOrder(warehouseManager, salesManager, "5", "5");
        ApprovedOrder another = createApprovedOrder(warehouseManager, salesManager, "5", "5");

        notifyShipment(order.salesToken(), order.salesOrderId(), doLine(another.salesOrderLineId(), "1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DO_LINE_INVALID"));
        notifyShipment(
                order.salesToken(),
                order.salesOrderId(),
                doLine(order.salesOrderLineId(), "1") + "," + doLine(order.salesOrderLineId(), "1")
        )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DO_LINE_INVALID"));
        //「個」不能有小數
        notifyShipment(order.salesToken(), order.salesOrderId(), doLine(order.salesOrderLineId(), "1.5"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DO_LINE_INVALID"));

        //倉管不能通知出貨
        notifyShipment(warehouse, order.salesOrderId(), doLine(order.salesOrderLineId(), "1"))
                .andExpect(status().isForbidden());

        Long deliveryOrderId = createDeliveryOrder(
                order.salesToken(), order.salesOrderId(), doLine(order.salesOrderLineId(), "5")
        );

        getDeliveryOrder(warehouse, deliveryOrderId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.delivery.address").value("台中市西屯區工業區一路 1 號"))
                .andExpect(jsonPath("$.lines[0].itemCode").value(order.item().getItemCode()))
                .andExpect(jsonPath("$.lines[0].unitPriceExcludingTax").doesNotExist())
                .andExpect(jsonPath("$.lines[0].lineAmountExcludingTax").doesNotExist());
    }

    @Test
    void quantityInDeliveryCannotBeCancelledUntilWithdrawn() throws Exception {
        String warehouseManager = login(createUser("wh-manager", "WAREHOUSE_MANAGER"));
        String salesManager = login(createUser("sales-manager", "SALES_MANAGER"));
        ApprovedOrder order = createApprovedOrder(warehouseManager, salesManager, "5", "5");

        Long deliveryOrderId = createDeliveryOrder(
                order.salesToken(), order.salesOrderId(), doLine(order.salesOrderLineId(), "5")
        );

        //5 個都排入出貨，可取消量是 0
        createCancellation(order.salesToken(), order.salesOrderId(), cancellationLine(order.salesOrderLineId(), "2"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SO_CANCELLATION_QUANTITY_EXCEEDED"))
                .andExpect(jsonPath("$.message", containsString("已排入出貨")));

        withdraw(order.salesToken(), deliveryOrderId, deliveryOrderVersion(order.salesToken(), deliveryOrderId))
                .andExpect(status().isOk());

        Long cancellationId = createSubmittedCancellation(
                order.salesToken(), order.salesOrderId(), cancellationLine(order.salesOrderLineId(), "2")
        );
        approveCancellation(salesManager, cancellationId).andExpect(status().isOk());

        assertThat(reservedQty(order.item())).isEqualByComparingTo("3");
        assertConsistent(warehouseManager, order.item());
    }

    @Test
    void cancellationApprovalRechecksDeliveryCreatedAfterSubmission() throws Exception {
        String warehouseManager = login(createUser("wh-manager", "WAREHOUSE_MANAGER"));
        String salesManager = login(createUser("sales-manager", "SALES_MANAGER"));
        ApprovedOrder order = createApprovedOrder(warehouseManager, salesManager, "5", "5");

        //送審時還沒有 DO，取消 2 通過
        Long cancellationId = createSubmittedCancellation(
                order.salesToken(), order.salesOrderId(), cancellationLine(order.salesOrderLineId(), "2")
        );

        //送審後才把 5 個全部排入出貨
        createDeliveryOrder(order.salesToken(), order.salesOrderId(), doLine(order.salesOrderLineId(), "5"));

        approveCancellation(salesManager, cancellationId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SO_CANCELLATION_QUANTITY_EXCEEDED"));

        //整個核准回滾，預留沒有被釋放
        assertThat(reservedQty(order.item())).isEqualByComparingTo("5");
        assertConsistent(warehouseManager, order.item());
    }
}
