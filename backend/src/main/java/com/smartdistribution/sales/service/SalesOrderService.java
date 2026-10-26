package com.smartdistribution.sales.service;

import com.smartdistribution.sales.dto.request.CancelSalesOrderRequest;
import com.smartdistribution.sales.dto.request.CreateSalesOrderRequest;
import com.smartdistribution.sales.dto.request.SubmitSalesOrderRequest;
import com.smartdistribution.sales.dto.request.UpdateSalesOrderRequest;
import com.smartdistribution.sales.dto.response.SalesOrderDetailResponse;
import com.smartdistribution.sales.dto.response.SalesOrderSummaryResponse;
import com.smartdistribution.sales.entity.SalesOrderStatus;
import com.smartdistribution.shared.web.response.PageResponse;
import org.springframework.data.domain.Pageable;

public interface SalesOrderService {

    PageResponse<SalesOrderSummaryResponse> findSalesOrders(
            String keyword,
            SalesOrderStatus status,
            Long customerId,
            Pageable pageable
    );

    SalesOrderDetailResponse findSalesOrder(Long salesOrderId);

    SalesOrderDetailResponse createSalesOrder(CreateSalesOrderRequest request);

    SalesOrderDetailResponse updateSalesOrder(Long salesOrderId, UpdateSalesOrderRequest request);

    /**
     * 送審：檢查主檔、單位與 ACTIVE 價格，保存交易快照後建立核准申請
     */
    SalesOrderDetailResponse submitSalesOrder(Long salesOrderId, SubmitSalesOrderRequest request);

    /**
     * 放棄草稿：DRAFT / RETURNED → CANCELLED
     */
    SalesOrderDetailResponse cancelSalesOrder(Long salesOrderId, CancelSalesOrderRequest request);
}
