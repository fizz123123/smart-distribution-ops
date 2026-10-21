package com.smartdistribution.masterdata.customerprice.controller;

import com.smartdistribution.masterdata.customerprice.dto.request.CreateCustomerPriceRequest;
import com.smartdistribution.masterdata.customerprice.dto.request.RevokeCustomerPriceRequest;
import com.smartdistribution.masterdata.customerprice.dto.response.CustomerPriceResponse;
import com.smartdistribution.masterdata.customerprice.entity.CustomerPriceStatus;
import com.smartdistribution.masterdata.customerprice.service.CustomerPriceService;
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
@RequestMapping("/api/v1/customer-prices")
@RequiredArgsConstructor
public class CustomerPriceController {

    private final CustomerPriceService customerPriceService;

    @GetMapping
    public ResponseEntity<PageResponse<CustomerPriceResponse>> findPrices(
            @RequestParam(required = false) Long customerItemMappingId,
            @RequestParam(required = false) CustomerPriceStatus status,
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC)
            Pageable pageable
    ) {
        PageResponse<CustomerPriceResponse> pageResponse =
                customerPriceService.findPrices(customerItemMappingId, status, pageable);

        return ResponseEntity.ok(pageResponse);
    }

    @GetMapping("/{priceId}")
    public ResponseEntity<CustomerPriceResponse> findPrice(@PathVariable Long priceId) {
        CustomerPriceResponse response = customerPriceService.findPrice(priceId);

        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<CustomerPriceResponse> createPrice(
            @Valid @RequestBody CreateCustomerPriceRequest request
    ) {
        CustomerPriceResponse response = customerPriceService.createPrice(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @PostMapping("/{priceId}/revoke")
    public ResponseEntity<CustomerPriceResponse> revokePrice(
            @PathVariable Long priceId,
            @Valid @RequestBody RevokeCustomerPriceRequest request
    ) {
        CustomerPriceResponse response = customerPriceService.revokePrice(priceId, request);

        return ResponseEntity.ok(response);
    }
}
