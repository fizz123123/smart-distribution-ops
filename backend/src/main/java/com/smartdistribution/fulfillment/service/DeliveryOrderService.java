package com.smartdistribution.fulfillment.service;

import com.smartdistribution.document.service.StoredDocument;
import com.smartdistribution.fulfillment.dto.request.CreateDeliveryOrderRequest;
import com.smartdistribution.fulfillment.dto.request.DeliveryOrderReasonRequest;
import com.smartdistribution.fulfillment.dto.request.DeliveryOrderVersionRequest;
import com.smartdistribution.fulfillment.dto.request.PostDeliveryOrderRequest;
import com.smartdistribution.fulfillment.dto.request.ReadyDeliveryOrderRequest;
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
     * 倉管作廢草稿或已完成揀貨的 DO；已完成揀貨的出貨單一併作廢，不動庫存；已過帳的不能作廢
     */
    DeliveryOrderDetailResponse voidDeliveryOrder(Long deliveryOrderId, DeliveryOrderReasonRequest request);

    /**
     * 完成揀貨：倉管回報每一筆實際找到多少（可為 0，至少一筆大於 0），依實際量產生三聯出貨單
     * 少於預計量的差額不使用預留，業務可以之後再通知出貨
     */
    DeliveryOrderDetailResponse readyDeliveryOrder(Long deliveryOrderId, ReadyDeliveryOrderRequest request);

    /**
     * 出貨過帳：交給司機時執行；依實際量扣減實體庫存並使用預留，全部出完時 SO 結案
     */
    DeliveryOrderDetailResponse postDeliveryOrder(Long deliveryOrderId, PostDeliveryOrderRequest request);

    //下載完成揀貨時產生的出貨單；作廢的也可以下載
    StoredDocument downloadDocument(Long deliveryOrderId);
}
