package com.smartdistribution.fulfillment.service;

import com.smartdistribution.fulfillment.dto.request.CreateDeliveryOrderRequest;
import com.smartdistribution.fulfillment.dto.request.DeliveryOrderReasonRequest;
import com.smartdistribution.fulfillment.dto.request.DeliveryOrderVersionRequest;
import com.smartdistribution.fulfillment.dto.response.DeliveryOrderDetailResponse;
import com.smartdistribution.fulfillment.dto.response.DeliveryOrderSummaryResponse;
import com.smartdistribution.fulfillment.entity.DeliveryOrderStatus;
import com.smartdistribution.shared.web.response.PageResponse;
import org.springframework.data.domain.Pageable;

public interface DeliveryOrderService {

    PageResponse<DeliveryOrderSummaryResponse> findDeliveryOrders(
            String keyword,
            DeliveryOrderStatus status,
            Long salesOrderId,
            Pageable pageable
    );

    DeliveryOrderDetailResponse findDeliveryOrder(Long deliveryOrderId);

    /**
     * 業務通知出貨，建立 DO 草稿：SO 必須已核准；每筆數量不能超過「已預留、還沒排入其他 DO」的部分
     */
    DeliveryOrderDetailResponse createDeliveryOrder(Long salesOrderId, CreateDeliveryOrderRequest request);

    /**
     * 業務撤回自己的出貨通知；只有倉管還沒揀貨的草稿可以撤回
     */
    DeliveryOrderDetailResponse withdrawDeliveryOrder(Long deliveryOrderId, DeliveryOrderVersionRequest request);

    /**
     * 倉管作廢草稿或已完成揀貨的 DO；已過帳的不能作廢
     */
    DeliveryOrderDetailResponse voidDeliveryOrder(Long deliveryOrderId, DeliveryOrderReasonRequest request);
}
