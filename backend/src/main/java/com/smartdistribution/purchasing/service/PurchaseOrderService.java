package com.smartdistribution.purchasing.service;

import com.smartdistribution.document.service.StoredDocument;
import com.smartdistribution.masterdata.common.UnitOfMeasure;
import com.smartdistribution.purchasing.dto.request.CreatePurchaseOrderRequest;
import com.smartdistribution.purchasing.dto.request.UpdatePurchaseOrderRequest;
import com.smartdistribution.purchasing.dto.response.LastPriceResponse;
import com.smartdistribution.purchasing.dto.response.PurchaseOrderDetailResponse;
import com.smartdistribution.purchasing.dto.response.PurchaseOrderSummaryResponse;
import com.smartdistribution.purchasing.entity.PurchaseOrderStatus;
import com.smartdistribution.shared.web.request.ReasonRequest;
import com.smartdistribution.shared.web.request.VersionRequest;
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
    PurchaseOrderDetailResponse submitPurchaseOrder(Long purchaseOrderId, VersionRequest request);

    PurchaseOrderDetailResponse cancelDraft(Long purchaseOrderId, VersionRequest request);

    /**
     * 取消已核准、還沒有任何收貨的 PO：明細全部轉為取消，正式 PDF 作廢
     * 採購在途跟著消失，來源 SO 的缺料會重新出現在尚待規劃
     */
    PurchaseOrderDetailResponse cancelApproved(Long purchaseOrderId, ReasonRequest request);

    /**
     * 短收結案：已部分收貨的 PO，還沒收齊的明細不再等，剩下的數量從採購在途扣除
     * 已入庫的數量不變；PDF 不作廢，因為這張 PO 確實有交易
     */
    PurchaseOrderDetailResponse closeShort(Long purchaseOrderId, ReasonRequest request);

    //下載核准時產生的正式 PDF；作廢的 PDF 也可以下載
    StoredDocument downloadDocument(Long purchaseOrderId);
}
