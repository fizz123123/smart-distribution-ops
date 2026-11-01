package com.smartdistribution.purchasing.service;

import com.smartdistribution.document.service.StoredDocument;
import com.smartdistribution.masterdata.common.UnitOfMeasure;
import com.smartdistribution.purchasing.dto.request.CreatePurchaseOrderRequest;
import com.smartdistribution.purchasing.dto.request.PurchaseOrderVersionRequest;
import com.smartdistribution.purchasing.dto.request.UpdatePurchaseOrderRequest;
import com.smartdistribution.purchasing.dto.response.LastPriceResponse;
import com.smartdistribution.purchasing.dto.response.PurchaseOrderDetailResponse;
import com.smartdistribution.purchasing.dto.response.PurchaseOrderSummaryResponse;
import com.smartdistribution.purchasing.entity.PurchaseOrderStatus;
import com.smartdistribution.shared.web.response.PageResponse;
import org.springframework.data.domain.Pageable;

public interface PurchaseOrderService {

    PageResponse<PurchaseOrderSummaryResponse> findPurchaseOrders(
            String keyword,
            PurchaseOrderStatus status,
            Long supplierId,
            Pageable pageable
    );

    PurchaseOrderDetailResponse findPurchaseOrder(Long purchaseOrderId);

    //建立明細時帶入參考單價；從未採購過時回傳 null
    LastPriceResponse findLastPrice(Long supplierId, Long companyItemId, UnitOfMeasure unit);

    /**
     * 建立草稿。明細可以關聯已核准 SO 的明細，數量不能超過那筆明細還能規劃的量；
     * 不關聯就是一般補庫
     */
    PurchaseOrderDetailResponse createPurchaseOrder(CreatePurchaseOrderRequest request);

    PurchaseOrderDetailResponse updatePurchaseOrder(Long purchaseOrderId, UpdatePurchaseOrderRequest request);

    /**
     * 送審：檢查預計到貨日與單價都已填寫，重新檢查主檔與來源 SO 的數量，計算未稅總額
     */
    PurchaseOrderDetailResponse submitPurchaseOrder(Long purchaseOrderId, PurchaseOrderVersionRequest request);

    PurchaseOrderDetailResponse cancelDraft(Long purchaseOrderId, PurchaseOrderVersionRequest request);

    //下載核准時產生的正式 PDF；作廢的 PDF 也可以下載
    StoredDocument downloadDocument(Long purchaseOrderId);
}
