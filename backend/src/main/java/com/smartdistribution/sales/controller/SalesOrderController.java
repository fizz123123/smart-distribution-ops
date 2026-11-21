package com.smartdistribution.sales.controller;

import com.smartdistribution.sales.dto.request.CreateSalesOrderRequest;
import com.smartdistribution.sales.dto.request.UpdateSalesOrderRequest;
import com.smartdistribution.sales.dto.response.SalesOrderConfirmationFile;
import com.smartdistribution.sales.dto.response.SalesOrderDetailResponse;
import com.smartdistribution.sales.dto.response.SalesOrderShortageResponse;
import com.smartdistribution.sales.dto.response.SalesOrderSummaryResponse;
import com.smartdistribution.sales.entity.SalesOrderStatus;
import com.smartdistribution.sales.service.SalesOrderService;
import com.smartdistribution.shared.web.request.VersionRequest;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/sales-orders")
@RequiredArgsConstructor
public class SalesOrderController {

    private final SalesOrderService salesOrderService;

    @GetMapping
    public ResponseEntity<PageResponse<SalesOrderSummaryResponse>> findSalesOrders(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) SalesOrderStatus status,
            @RequestParam(required = false) Long customerId,
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC)
            Pageable pageable
    ) {
        return ResponseEntity.ok(salesOrderService.findSalesOrders(keyword, status, customerId, pageable));
    }

    //路徑比 /{salesOrderId} 具體，Spring 會優先對應到這裡
    //不分頁：尚待規劃要逐行即時計算，無法在資料庫分頁；採購也需要看到全部才能依供應商分組
    @GetMapping("/shortages")
    public ResponseEntity<List<SalesOrderShortageResponse>> findShortages(
            @RequestParam(required = false) Long companyItemId,
            @RequestParam(required = false) Long preferredSupplierId
    ) {
        return ResponseEntity.ok(salesOrderService.findShortages(companyItemId, preferredSupplierId));
    }

    @GetMapping("/{salesOrderId}")
    public ResponseEntity<SalesOrderDetailResponse> findSalesOrder(@PathVariable Long salesOrderId) {
        return ResponseEntity.ok(salesOrderService.findSalesOrder(salesOrderId));
    }

    @GetMapping("/{salesOrderId}/confirmation")
    public ResponseEntity<byte[]> printConfirmation(@PathVariable Long salesOrderId) {
        SalesOrderConfirmationFile file = salesOrderService.printConfirmation(salesOrderId);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(file.fileName()).build().toString()
                )
                .body(file.content());
    }

    @PostMapping
    public ResponseEntity<SalesOrderDetailResponse> createSalesOrder(
            @Valid @RequestBody CreateSalesOrderRequest request
    ) {
        SalesOrderDetailResponse response = salesOrderService.createSalesOrder(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{salesOrderId}")
    public ResponseEntity<SalesOrderDetailResponse> updateSalesOrder(
            @PathVariable Long salesOrderId,
            @Valid @RequestBody UpdateSalesOrderRequest request
    ) {
        return ResponseEntity.ok(salesOrderService.updateSalesOrder(salesOrderId, request));
    }

    @PostMapping("/{salesOrderId}/submit")
    public ResponseEntity<SalesOrderDetailResponse> submitSalesOrder(
            @PathVariable Long salesOrderId,
            @Valid @RequestBody VersionRequest request
    ) {
        return ResponseEntity.ok(salesOrderService.submitSalesOrder(salesOrderId, request));
    }

    @PostMapping("/{salesOrderId}/cancel")
    public ResponseEntity<SalesOrderDetailResponse> cancelSalesOrder(
            @PathVariable Long salesOrderId,
            @Valid @RequestBody VersionRequest request
    ) {
        return ResponseEntity.ok(salesOrderService.cancelSalesOrder(salesOrderId, request));
    }
}
