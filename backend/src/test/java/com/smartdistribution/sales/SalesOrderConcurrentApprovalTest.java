package com.smartdistribution.sales;

import com.smartdistribution.masterdata.common.UnitOfMeasure;
import com.smartdistribution.masterdata.companyitem.entity.CompanyItem;
import com.smartdistribution.masterdata.customer.entity.Customer;
import com.smartdistribution.masterdata.customer.entity.PaymentTerm;
import com.smartdistribution.masterdata.customeritemmapping.entity.CustomerItemMapping;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 兩張 SO 同時核准並競爭同一料號，不得超額預留（AT-023）。
 * 測試本身不加 @Transactional：兩個執行緒必須各自提交交易，才會真的在餘額列上互相等待。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SalesOrderConcurrentApprovalTest extends SalesOrderTestSupport {

    @Test
    void concurrentApprovalsNeverOverReserve() throws Exception {
        String sales = login(createUser("race-sales", "SALES"));
        String firstManager = login(createUser("race-manager-1", "SALES_MANAGER"));
        String secondManager = login(createUser("race-manager-2", "SALES_MANAGER"));
        String warehouseManager = login(createUser("race-warehouse", "WAREHOUSE_MANAGER"));

        Customer customer = createActiveCustomer(PaymentTerm.DUE_0);
        CompanyItem pu = createActiveItem(UnitOfMeasure.PCS);
        CustomerItemMapping mapping = createActiveMapping(customer, pu);
        createActivePrice(mapping, UnitOfMeasure.PCS, "200");
        postOpeningBalance(warehouseManager, pu, "2");

        // 兩張 SO 各要 2 個，可用量只有 2 個
        Long firstOrderId = createSubmittedOrder(sales, draftBody(customer, line(mapping, "2", UnitOfMeasure.PCS)));
        Long secondOrderId = createSubmittedOrder(sales, draftBody(customer, line(mapping, "2", UnitOfMeasure.PCS)));

        // 兩個執行緒都準備好之後才同時送出核准
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<Integer> first = executor.submit(approveAfter(start, firstManager, firstOrderId));
            Future<Integer> second = executor.submit(approveAfter(start, secondManager, secondOrderId));
            start.countDown();

            // 兩張都核准成功：後到的一方會等前一方提交，再依剩下的可用量（0）建立預留
            assertThat(first.get(30, TimeUnit.SECONDS)).isEqualTo(200);
            assertThat(second.get(30, TimeUnit.SECONDS)).isEqualTo(200);
        } finally {
            executor.shutdownNow();
        }

        assertThat(reservedQty(pu)).isEqualByComparingTo("2");
        assertThat(onHandQty(pu)).isEqualByComparingTo("2");
        assertConsistent(warehouseManager, pu);
    }

    private Callable<Integer> approveAfter(CountDownLatch start, String token, Long salesOrderId) {
        return () -> {
            start.await(10, TimeUnit.SECONDS);
            return approve(token, salesOrderId).andReturn().getResponse().getStatus();
        };
    }
}
