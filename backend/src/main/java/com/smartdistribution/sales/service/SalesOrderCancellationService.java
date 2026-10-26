package com.smartdistribution.sales.service;

import com.smartdistribution.sales.dto.request.CreateSalesOrderCancellationRequest;
import com.smartdistribution.sales.dto.request.SubmitSalesOrderCancellationRequest;
import com.smartdistribution.sales.dto.request.UpdateSalesOrderCancellationRequest;
import com.smartdistribution.sales.dto.request.WithdrawSalesOrderCancellationRequest;
import com.smartdistribution.sales.dto.response.SalesOrderCancellationResponse;

import java.util.List;

/**
 * 未出貨餘量取消申請；核准與退回走共用核准 API
 */
public interface SalesOrderCancellationService {

    List<SalesOrderCancellationResponse> findCancellations(Long salesOrderId);

    SalesOrderCancellationResponse findCancellation(Long cancellationId);

    SalesOrderCancellationResponse createCancellation(
            Long salesOrderId,
            CreateSalesOrderCancellationRequest request
    );

    SalesOrderCancellationResponse updateCancellation(
            Long cancellationId,
            UpdateSalesOrderCancellationRequest request
    );

    SalesOrderCancellationResponse submitCancellation(
            Long cancellationId,
            SubmitSalesOrderCancellationRequest request
    );

    //撤回不想送出的申請：DRAFT / RETURNED → WITHDRAWN
    SalesOrderCancellationResponse withdrawCancellation(
            Long cancellationId,
            WithdrawSalesOrderCancellationRequest request
    );
}
