package com.smartdistribution.pricing;

import com.smartdistribution.masterdata.common.UnitOfMeasure;
import com.smartdistribution.masterdata.companyitem.entity.CompanyItem;
import com.smartdistribution.masterdata.customer.entity.PaymentTerm;
import com.smartdistribution.masterdata.customeritemmapping.entity.CustomerItemMapping;
import com.smartdistribution.pricing.entity.CustomerPrice;
import com.smartdistribution.pricing.entity.StandardPrice;
import com.smartdistribution.sales.SalesOrderTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 建立 SO 時預填價格的查詢：目前適用的價格與下一次變動，規則和送審時取價相同
 * 借用 SO 測試的資料準備方法建立客戶、料號與已核准的價格
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApplicablePriceApiIntegrationTest extends SalesOrderTestSupport {

    @Autowired
    private Clock clock;

    @Test
    void standardPriceAppliesUntilApprovedCustomerPriceStarts() throws Exception {
        String sales = login(createUser("applicable-sales", "SALES"));
        CompanyItem item = createActiveItem(UnitOfMeasure.PCS);
        CustomerItemMapping mapping = createActiveMapping(createActiveCustomer(PaymentTerm.DUE_0), item);
        LocalDate today = LocalDate.now(clock);

        //系統測試的情境：客戶價格已核准但還沒到生效日，今天適用的是標準售價
        StandardPrice standardPrice = createActiveStandardPrice(item, UnitOfMeasure.PCS, "1200", today.minusDays(30));
        CustomerPrice customerPrice = createActivePrice(mapping, UnitOfMeasure.PCS, "1150", today.plusDays(5));

        mockMvc.perform(get("/api/v1/prices/applicable")
                        .header("Authorization", bearer(sales))
                        .param("customerItemMappingId", mapping.getId().toString())
                        .param("unit", "PCS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value(today.toString()))
                .andExpect(jsonPath("$.current.source").value("STANDARD_PRICE"))
                .andExpect(jsonPath("$.current.priceId").value(standardPrice.getId()))
                .andExpect(jsonPath("$.current.unitPriceExcludingTax").value(1200))
                .andExpect(jsonPath("$.current.unitPriceEditable").value(true))
                .andExpect(jsonPath("$.next.appliesFrom").value(today.plusDays(5).toString()))
                .andExpect(jsonPath("$.next.price.source").value("CUSTOMER_PRICE"))
                .andExpect(jsonPath("$.next.price.priceId").value(customerPrice.getId()))
                .andExpect(jsonPath("$.next.price.unitPriceEditable").value(false));
    }

    @Test
    void endingPriceWithoutFallbackIsReportedAsNoPrice() throws Exception {
        String sales = login(createUser("applicable-ending", "SALES"));
        CustomerItemMapping mapping = createActiveMapping(
                createActiveCustomer(PaymentTerm.DUE_0),
                createActiveItem(UnitOfMeasure.PCS)
        );
        LocalDate today = LocalDate.now(clock);

        //客戶價格 3 天後到期，又沒有標準售價：之後就沒有價格可用
        CustomerPrice price = new CustomerPrice(
                mapping,
                UnitOfMeasure.PCS,
                new BigDecimal("980"),
                null,
                today.plusDays(3),
                1L
        );
        price.approve(1L, Instant.now(), today.minusDays(10));
        customerPriceRepository.saveAndFlush(price);

        mockMvc.perform(get("/api/v1/prices/applicable")
                        .header("Authorization", bearer(sales))
                        .param("customerItemMappingId", mapping.getId().toString())
                        .param("unit", "PCS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.current.source").value("CUSTOMER_PRICE"))
                .andExpect(jsonPath("$.current.effectiveTo").value(today.plusDays(3).toString()))
                .andExpect(jsonPath("$.next.appliesFrom").value(today.plusDays(4).toString()))
                .andExpect(jsonPath("$.next.price").value(nullValue()));

        //補登的訂單日期在價格生效前，當天沒有價格；之後價格開始生效
        mockMvc.perform(get("/api/v1/prices/applicable")
                        .header("Authorization", bearer(sales))
                        .param("customerItemMappingId", mapping.getId().toString())
                        .param("unit", "PCS")
                        .param("date", today.minusDays(20).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.current").value(nullValue()))
                .andExpect(jsonPath("$.next.appliesFrom").value(today.minusDays(10).toString()))
                .andExpect(jsonPath("$.next.price.priceId").value(price.getId()));
    }

    @Test
    void noPriceDateRulesAndPermissions() throws Exception {
        String sales = login(createUser("applicable-none", "SALES"));
        String warehouse = login(createUser("applicable-warehouse", "WAREHOUSE"));
        CustomerItemMapping mapping = createActiveMapping(
                createActiveCustomer(PaymentTerm.DUE_0),
                createActiveItem(UnitOfMeasure.PCS)
        );
        LocalDate today = LocalDate.now(clock);

        mockMvc.perform(get("/api/v1/prices/applicable")
                        .header("Authorization", bearer(sales))
                        .param("customerItemMappingId", mapping.getId().toString())
                        .param("unit", "PCS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.current").value(nullValue()))
                .andExpect(jsonPath("$.next").value(nullValue()));

        //和 SO 的訂單日期一樣不能是未來
        mockMvc.perform(get("/api/v1/prices/applicable")
                        .header("Authorization", bearer(sales))
                        .param("customerItemMappingId", mapping.getId().toString())
                        .param("unit", "PCS")
                        .param("date", today.plusDays(1).toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mockMvc.perform(get("/api/v1/prices/applicable")
                        .header("Authorization", bearer(sales))
                        .param("customerItemMappingId", "999999999")
                        .param("unit", "PCS"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CUSTOMER_ITEM_MAPPING_NOT_FOUND"));

        //只給建立 SO 的人
        mockMvc.perform(get("/api/v1/prices/applicable")
                        .header("Authorization", bearer(warehouse))
                        .param("customerItemMappingId", mapping.getId().toString())
                        .param("unit", "PCS"))
                .andExpect(status().isForbidden());
    }
}
