package com.smartdistribution.masterdata.customeritemmapping.controller;

import com.smartdistribution.masterdata.common.MasterDataStatus;
import com.smartdistribution.masterdata.customeritemmapping.dto.request.ChangeCustomerItemMappingStatusRequest;
import com.smartdistribution.masterdata.customeritemmapping.dto.request.CreateCustomerItemMappingRequest;
import com.smartdistribution.masterdata.customeritemmapping.dto.request.SubmitCustomerItemMappingRequest;
import com.smartdistribution.masterdata.customeritemmapping.dto.request.UpdateCustomerItemMappingRequest;
import com.smartdistribution.masterdata.customeritemmapping.dto.response.CustomerItemMappingResponse;
import com.smartdistribution.masterdata.customeritemmapping.service.CustomerItemMappingService;
import com.smartdistribution.shared.web.response.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
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
@RequestMapping("/api/v1/customer-item-mappings")
@RequiredArgsConstructor
public class CustomerItemMappingController {

    private final CustomerItemMappingService customerItemMappingService;

    @GetMapping
    public ResponseEntity<PageResponse<CustomerItemMappingResponse>> findMappings(
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) MasterDataStatus status,
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC)
            Pageable pageable
    ) {
        PageResponse<CustomerItemMappingResponse> pageResponse =
                customerItemMappingService.findMappings(customerId, keyword, status, pageable);

        return ResponseEntity.ok(pageResponse);
    }

    @GetMapping("/{mappingId}")
    public ResponseEntity<CustomerItemMappingResponse> findMapping(@PathVariable Long mappingId) {
        CustomerItemMappingResponse response = customerItemMappingService.findMapping(mappingId);

        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<CustomerItemMappingResponse> createMapping(
            @Valid @RequestBody CreateCustomerItemMappingRequest request
    ) {
        CustomerItemMappingResponse response = customerItemMappingService.createMapping(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @PatchMapping("/{mappingId}")
    public ResponseEntity<CustomerItemMappingResponse> updateMapping(
            @PathVariable Long mappingId,
            @Valid @RequestBody UpdateCustomerItemMappingRequest request
    ) {
        CustomerItemMappingResponse response =
                customerItemMappingService.updateMapping(mappingId, request);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/{mappingId}/submit")
    public ResponseEntity<CustomerItemMappingResponse> submitMapping(
            @PathVariable Long mappingId,
            @Valid @RequestBody SubmitCustomerItemMappingRequest request
    ) {
        CustomerItemMappingResponse response =
                customerItemMappingService.submitMapping(mappingId, request);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/{mappingId}/deactivate")
    public ResponseEntity<CustomerItemMappingResponse> deactivateMapping(
            @PathVariable Long mappingId,
            @Valid @RequestBody ChangeCustomerItemMappingStatusRequest request
    ) {
        CustomerItemMappingResponse response =
                customerItemMappingService.deactivateMapping(mappingId, request);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/{mappingId}/activate")
    public ResponseEntity<CustomerItemMappingResponse> activateMapping(
            @PathVariable Long mappingId,
            @Valid @RequestBody ChangeCustomerItemMappingStatusRequest request
    ) {
        CustomerItemMappingResponse response =
                customerItemMappingService.activateMapping(mappingId, request);

        return ResponseEntity.ok(response);
    }
}
