package com.smartdistribution.purchasing.controller;

import com.smartdistribution.document.service.StoredDocument;
import com.smartdistribution.masterdata.common.UnitOfMeasure;
import com.smartdistribution.purchasing.dto.request.CreatePurchaseOrderRequest;
import com.smartdistribution.purchasing.dto.request.PurchaseOrderVersionRequest;
import com.smartdistribution.purchasing.dto.request.UpdatePurchaseOrderRequest;
import com.smartdistribution.purchasing.dto.response.LastPriceResponse;
import com.smartdistribution.purchasing.dto.response.PurchaseOrderDetailResponse;
import com.smartdistribution.purchasing.dto.response.PurchaseOrderSummaryResponse;
import com.smartdistribution.purchasing.entity.PurchaseOrderStatus;
import com.smartdistribution.purchasing.service.PurchaseOrderService;
import com.smartdistribution.shared.web.response.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/purchase-orders")
@RequiredArgsConstructor
public class PurchaseOrderController {

    private final PurchaseOrderService purchaseOrderService;

    @GetMapping
    public ResponseEntity<PageResponse<PurchaseOrderSummaryResponse>> findPurchaseOrders(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) PurchaseOrderStatus status,
            @RequestParam(required = false) Long supplierId,
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC)
            Pageable pageable
    ) {
        return ResponseEntity.ok(purchaseOrderService.findPurchaseOrders(keyword, status, supplierId, pageable));
    }

    @GetMapping("/last-price")
    public ResponseEntity<LastPriceResponse> findLastPrice(
            @RequestParam Long supplierId,
            @RequestParam Long companyItemId,
            @RequestParam UnitOfMeasure unit
    ) {
        return ResponseEntity.ok(purchaseOrderService.findLastPrice(supplierId, companyItemId, unit));
    }

    @GetMapping("/{purchaseOrderId}")
    public ResponseEntity<PurchaseOrderDetailResponse> findPurchaseOrder(@PathVariable Long purchaseOrderId) {
        return ResponseEntity.ok(purchaseOrderService.findPurchaseOrder(purchaseOrderId));
    }

    @PostMapping
    public ResponseEntity<PurchaseOrderDetailResponse> createPurchaseOrder(
            @Valid @RequestBody CreatePurchaseOrderRequest request
    ) {
        PurchaseOrderDetailResponse response = purchaseOrderService.createPurchaseOrder(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PatchMapping("/{purchaseOrderId}")
    public ResponseEntity<PurchaseOrderDetailResponse> updatePurchaseOrder(
            @PathVariable Long purchaseOrderId,
            @Valid @RequestBody UpdatePurchaseOrderRequest request
    ) {
        return ResponseEntity.ok(purchaseOrderService.updatePurchaseOrder(purchaseOrderId, request));
    }

    @PostMapping("/{purchaseOrderId}/submit")
    public ResponseEntity<PurchaseOrderDetailResponse> submitPurchaseOrder(
            @PathVariable Long purchaseOrderId,
            @Valid @RequestBody PurchaseOrderVersionRequest request
    ) {
        return ResponseEntity.ok(purchaseOrderService.submitPurchaseOrder(purchaseOrderId, request));
    }

    @PostMapping("/{purchaseOrderId}/cancel-draft")
    public ResponseEntity<PurchaseOrderDetailResponse> cancelDraft(
            @PathVariable Long purchaseOrderId,
            @Valid @RequestBody PurchaseOrderVersionRequest request
    ) {
        return ResponseEntity.ok(purchaseOrderService.cancelDraft(purchaseOrderId, request));
    }

    @GetMapping("/{purchaseOrderId}/document")
    public ResponseEntity<byte[]> downloadDocument(@PathVariable Long purchaseOrderId) {
        StoredDocument document = purchaseOrderService.downloadDocument(purchaseOrderId);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(document.fileName()).build().toString()
                )
                .body(document.content());
    }
}
