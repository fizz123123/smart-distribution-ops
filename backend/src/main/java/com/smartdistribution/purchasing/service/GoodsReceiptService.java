package com.smartdistribution.purchasing.service;

import com.smartdistribution.purchasing.dto.request.PostGoodsReceiptRequest;
import com.smartdistribution.purchasing.dto.response.GoodsReceiptResponse;
import com.smartdistribution.purchasing.dto.response.GoodsReceiptSummaryResponse;
import com.smartdistribution.purchasing.dto.response.OpenPurchaseOrderResponse;
import com.smartdistribution.shared.web.response.PageResponse;
import org.springframework.data.domain.Pageable;

public interface GoodsReceiptService {

    //已核准、還有未結數量的 PO；不含單價，給倉管點貨用
    PageResponse<OpenPurchaseOrderResponse> findOpenPurchaseOrders(Pageable pageable);

    //不指定 PO 時列出全部收貨紀錄
    PageResponse<GoodsReceiptSummaryResponse> findGoodsReceipts(Long purchaseOrderId, Pageable pageable);

    GoodsReceiptResponse findGoodsReceipt(Long goodsReceiptId);

    /**
     * 替 PO 收貨並入庫；收貨單一建立就生效，之後不能修改或沖銷
     * 只有合格數量會入庫：關聯 SO 的明細先補足該 SO 的預留，剩下的成為一般庫存
     * 損壞與規格不符只記錄、不入庫，這部分仍算未收，等供應商補貨或短收結案
     */
    GoodsReceiptResponse postGoodsReceipt(Long purchaseOrderId, PostGoodsReceiptRequest request);
}
