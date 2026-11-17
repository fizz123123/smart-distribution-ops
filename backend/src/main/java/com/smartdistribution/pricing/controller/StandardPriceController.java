package com.smartdistribution.pricing.controller;

import com.smartdistribution.pricing.dto.request.CreateStandardPriceRequest;
import com.smartdistribution.pricing.dto.request.RevokePriceRequest;
import com.smartdistribution.pricing.dto.response.StandardPriceResponse;
import com.smartdistribution.pricing.entity.PriceStatus;
import com.smartdistribution.pricing.service.StandardPriceService;
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
@RequestMapping("/api/v1/standard-prices")
@RequiredArgsConstructor
public class StandardPriceController {

    private final StandardPriceService standardPriceService;

    @GetMapping
    public ResponseEntity<PageResponse<StandardPriceResponse>> findPrices(
            @RequestParam(required = false) Long companyItemId,
            @RequestParam(required = false) PriceStatus status,
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC)
            Pageable pageable
    ) {
        PageResponse<StandardPriceResponse> pageResponse =
                standardPriceService.findPrices(companyItemId, status, pageable);

        return ResponseEntity.ok(pageResponse);
    }

    @GetMapping("/{priceId}")
    public ResponseEntity<StandardPriceResponse> findPrice(@PathVariable Long priceId) {
        StandardPriceResponse response = standardPriceService.findPrice(priceId);

        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<StandardPriceResponse> createPrice(
            @Valid @RequestBody CreateStandardPriceRequest request
    ) {
        StandardPriceResponse response = standardPriceService.createPrice(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @PostMapping("/{priceId}/revoke")
    public ResponseEntity<StandardPriceResponse> revokePrice(
            @PathVariable Long priceId,
            @Valid @RequestBody RevokePriceRequest request
    ) {
        StandardPriceResponse response = standardPriceService.revokePrice(priceId, request);

        return ResponseEntity.ok(response);
    }
}
