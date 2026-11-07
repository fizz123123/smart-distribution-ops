package com.smartdistribution.receivable;

import com.smartdistribution.fulfillment.DeliveryOrderTestSupport;
import com.smartdistribution.masterdata.common.UnitOfMeasure;
import com.smartdistribution.masterdata.companyitem.entity.CompanyItem;
import com.smartdistribution.masterdata.customer.entity.Customer;
import com.smartdistribution.masterdata.customer.entity.PaymentTerm;
import com.smartdistribution.masterdata.customeritemmapping.entity.CustomerItemMapping;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 應收整合測試共用的資料準備；客戶、料號、SO、DO 與帳號沿用 SO 與 DO 測試的工具
 * 應收的清理在 DeliveryOrderTestSupport，因為應收是 DO 過帳時建立的
 */
public abstract class ReceivableTestSupport extends DeliveryOrderTestSupport {

    /**
     * SO_MAIN：PE 10 支 @100、PU 5 個 @200，庫存剛好足夠，核准後全數預留
     * 一次全部出貨時，未稅 2,000、稅額 100、含稅 2,100
     */
    protected MainOrder createMainOrder(String warehouseManager, String salesManager, PaymentTerm paymentTerm)
            throws Exception {
        String sales = login(createUser("ar-sales", "SALES"));
        Customer customer = createActiveCustomer(paymentTerm);
        CompanyItem pe = createActiveItem(UnitOfMeasure.PCS);
        CompanyItem pu = createActiveItem(UnitOfMeasure.PCS);
        CustomerItemMapping peMapping = createActiveMapping(customer, pe);
        CustomerItemMapping puMapping = createActiveMapping(customer, pu);
        createActivePrice(peMapping, UnitOfMeasure.PCS, "100");
        createActivePrice(puMapping, UnitOfMeasure.PCS, "200");
        postOpeningBalance(warehouseManager, pe, "10");
        postOpeningBalance(warehouseManager, pu, "5");

        Long salesOrderId = createSubmittedOrder(sales, draftBody(
                customer,
                line(peMapping, "10", UnitOfMeasure.PCS) + "," + line(puMapping, "5", UnitOfMeasure.PCS)
        ));
        approve(salesManager, salesOrderId).andExpect(status().isOk());
        List<Long> lineIds = findLineIds(salesOrderId);

        return new MainOrder(salesOrderId, lineIds.get(0), lineIds.get(1), pe, pu, customer, sales);
    }

    /**
     * 通知出貨 PE、PU，倉管依實際量揀貨後過帳，回傳 DO ID
     */
    protected Long shipAndPost(
            MainOrder order,
            String warehouse,
            String peQuantity,
            String puQuantity,
            LocalDate actualShipmentDate
    ) throws Exception {
        Long deliveryOrderId = createDeliveryOrder(
                order.salesToken(),
                order.salesOrderId(),
                doLine(order.peLineId(), peQuantity) + "," + doLine(order.puLineId(), puQuantity)
        );
        List<Long> lineIds = findDeliveryOrderLineIds(deliveryOrderId);
        ready(warehouse, deliveryOrderId, actualLine(lineIds.get(0), peQuantity) + "," + actualLine(lineIds.get(1), puQuantity))
                .andExpect(status().isOk());
        postShipment(warehouse, deliveryOrderId, actualShipmentDate).andExpect(status().isOk());

        return deliveryOrderId;
    }

    protected Long findReceivableId(Long deliveryOrderId) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM receivable WHERE delivery_order_id = ?",
                Long.class,
                deliveryOrderId
        );
    }

    protected int countReceivables(Long deliveryOrderId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM receivable WHERE delivery_order_id = ?",
                Integer.class,
                deliveryOrderId
        );
    }

    protected ResultActions getReceivable(String token, Long receivableId) throws Exception {
        return mockMvc.perform(get("/api/v1/receivables/{id}", receivableId)
                .header("Authorization", bearer(token)));
    }

    /**
     * @param salesToken 建立這張 SO 的業務，通知出貨用它
     */
    protected record MainOrder(
            Long salesOrderId,
            Long peLineId,
            Long puLineId,
            CompanyItem pe,
            CompanyItem pu,
            Customer customer,
            String salesToken
    ) {
    }
}
