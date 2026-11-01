package com.smartdistribution.purchasing.controller;

import com.smartdistribution.purchasing.dto.request.PostGoodsReceiptRequest;
import com.smartdistribution.purchasing.dto.response.GoodsReceiptResponse;
import com.smartdistribution.purchasing.dto.response.GoodsReceiptSummaryResponse;
import com.smartdistribution.purchasing.dto.response.OpenPurchaseOrderResponse;
import com.smartdistribution.purchasing.service.GoodsReceiptService;
import com.smartdistribution.shared.web.response.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class GoodsReceiptController {

    private final GoodsReceiptService goodsReceiptService;

    @GetMapping("/goods-receipts/open-purchase-orders")
    public ResponseEntity<PageResponse<OpenPurchaseOrderResponse>> findOpenPurchaseOrders(
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.ASC)
            Pageable pageable
    ) {
        return ResponseEntity.ok(goodsReceiptService.findOpenPurchaseOrders(pageable));
    }

    @GetMapping("/goods-receipts")
    public ResponseEntity<PageResponse<GoodsReceiptSummaryResponse>> findGoodsReceipts(
            @RequestParam(required = false) Long purchaseOrderId,
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC)
            Pageable pageable
    ) {
        return ResponseEntity.ok(goodsReceiptService.findGoodsReceipts(purchaseOrderId, pageable));
    }

    @GetMapping("/goods-receipts/{goodsReceiptId}")
    public ResponseEntity<GoodsReceiptResponse> findGoodsReceipt(@PathVariable Long goodsReceiptId) {
        return ResponseEntity.ok(goodsReceiptService.findGoodsReceipt(goodsReceiptId));
    }

    //收貨掛在 PO 底下，路徑直接表達「替哪張 PO 收貨」
    @PostMapping("/purchase-orders/{purchaseOrderId}/goods-receipts")
    public ResponseEntity<GoodsReceiptResponse> postGoodsReceipt(
            @PathVariable Long purchaseOrderId,
            @Valid @RequestBody PostGoodsReceiptRequest request
    ) {
        GoodsReceiptResponse response = goodsReceiptService.postGoodsReceipt(purchaseOrderId, request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
