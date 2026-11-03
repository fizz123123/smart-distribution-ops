package com.smartdistribution.fulfillment.controller;

import com.smartdistribution.document.service.StoredDocument;
import com.smartdistribution.fulfillment.dto.request.CreateDeliveryOrderRequest;
import com.smartdistribution.fulfillment.dto.request.DeliveryOrderReasonRequest;
import com.smartdistribution.fulfillment.dto.request.DeliveryOrderVersionRequest;
import com.smartdistribution.fulfillment.dto.request.PostDeliveryOrderRequest;
import com.smartdistribution.fulfillment.dto.request.ReadyDeliveryOrderRequest;
import com.smartdistribution.fulfillment.dto.response.DeliveryOrderDetailResponse;
import com.smartdistribution.fulfillment.dto.response.DeliveryOrderSummaryResponse;
import com.smartdistribution.fulfillment.entity.DeliveryOrderStatus;
import com.smartdistribution.fulfillment.service.DeliveryOrderService;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class DeliveryOrderController {

    private final DeliveryOrderService deliveryOrderService;

    //通知出貨掛在 SO 底下，路徑表達 "替哪張 SO 出貨"
    @PostMapping("/sales-orders/{salesOrderId}/delivery-orders")
    public ResponseEntity<DeliveryOrderDetailResponse> createDeliveryOrder(
            @PathVariable Long salesOrderId,
            @Valid @RequestBody CreateDeliveryOrderRequest request
    ) {
        DeliveryOrderDetailResponse response = deliveryOrderService.createDeliveryOrder(salesOrderId, request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/delivery-orders")
    public ResponseEntity<PageResponse<DeliveryOrderSummaryResponse>> findDeliveryOrders(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) DeliveryOrderStatus status,
            @RequestParam(required = false) Long salesOrderId,
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC)
            Pageable pageable
    ) {
        return ResponseEntity.ok(deliveryOrderService.findDeliveryOrders(keyword, status, salesOrderId, pageable));
    }

    @GetMapping("/delivery-orders/{deliveryOrderId}")
    public ResponseEntity<DeliveryOrderDetailResponse> findDeliveryOrder(@PathVariable Long deliveryOrderId) {
        return ResponseEntity.ok(deliveryOrderService.findDeliveryOrder(deliveryOrderId));
    }

    @PostMapping("/delivery-orders/{deliveryOrderId}/withdraw")
    public ResponseEntity<DeliveryOrderDetailResponse> withdrawDeliveryOrder(
            @PathVariable Long deliveryOrderId,
            @Valid @RequestBody DeliveryOrderVersionRequest request
    ) {
        return ResponseEntity.ok(deliveryOrderService.withdrawDeliveryOrder(deliveryOrderId, request));
    }

    @PostMapping("/delivery-orders/{deliveryOrderId}/void")
    public ResponseEntity<DeliveryOrderDetailResponse> voidDeliveryOrder(
            @PathVariable Long deliveryOrderId,
            @Valid @RequestBody DeliveryOrderReasonRequest request
    ) {
        return ResponseEntity.ok(deliveryOrderService.voidDeliveryOrder(deliveryOrderId, request));
    }

    @PostMapping("/delivery-orders/{deliveryOrderId}/ready")
    public ResponseEntity<DeliveryOrderDetailResponse> readyDeliveryOrder(
            @PathVariable Long deliveryOrderId,
            @Valid @RequestBody ReadyDeliveryOrderRequest request
    ) {
        return ResponseEntity.ok(deliveryOrderService.readyDeliveryOrder(deliveryOrderId, request));
    }

    @PostMapping("/delivery-orders/{deliveryOrderId}/post")
    public ResponseEntity<DeliveryOrderDetailResponse> postDeliveryOrder(
            @PathVariable Long deliveryOrderId,
            @Valid @RequestBody PostDeliveryOrderRequest request
    ) {
        return ResponseEntity.ok(deliveryOrderService.postDeliveryOrder(deliveryOrderId, request));
    }

    @GetMapping("/delivery-orders/{deliveryOrderId}/document")
    public ResponseEntity<byte[]> downloadDocument(@PathVariable Long deliveryOrderId) {
        StoredDocument document = deliveryOrderService.downloadDocument(deliveryOrderId);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(document.fileName()).build().toString()
                )
                .body(document.content());
    }
}
