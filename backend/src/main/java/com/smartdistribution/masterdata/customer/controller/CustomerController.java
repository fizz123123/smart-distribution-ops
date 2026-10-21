package com.smartdistribution.masterdata.customer.controller;

import com.smartdistribution.masterdata.common.MasterDataStatus;
import com.smartdistribution.masterdata.customer.dto.request.ChangeCustomerPaymentTermRequest;
import com.smartdistribution.masterdata.customer.dto.request.ChangeCustomerStatusRequest;
import com.smartdistribution.masterdata.customer.dto.request.CreateCustomerRequest;
import com.smartdistribution.masterdata.customer.dto.request.SubmitCustomerRequest;
import com.smartdistribution.masterdata.customer.dto.request.UpdateCustomerRequest;
import com.smartdistribution.masterdata.customer.dto.response.CustomerResponse;
import com.smartdistribution.masterdata.customer.service.CustomerService;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;

    @GetMapping
    public ResponseEntity<PageResponse<CustomerResponse>> findCustomers(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) MasterDataStatus status,
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC)
            Pageable pageable
    ) {
        PageResponse<CustomerResponse> pageResponse =
                customerService.findCustomers(keyword, status, pageable);

        return ResponseEntity.ok(pageResponse);
    }

    @GetMapping("/{customerId}")
    public ResponseEntity<CustomerResponse> findCustomer(@PathVariable Long customerId) {
        CustomerResponse response = customerService.findCustomer(customerId);
        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<CustomerResponse> createCustomer(
            @Valid @RequestBody CreateCustomerRequest request
    ) {
        CustomerResponse response = customerService.createCustomer(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @PatchMapping("/{customerId}")
    public ResponseEntity<CustomerResponse> updateCustomer(
            @PathVariable Long customerId,
            @Valid @RequestBody UpdateCustomerRequest request
    ) {
        CustomerResponse response = customerService.updateCustomer(customerId, request);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/{customerId}/submit")
    public ResponseEntity<CustomerResponse> submitCustomer(
            @PathVariable Long customerId,
            @Valid @RequestBody SubmitCustomerRequest request
    ) {
        CustomerResponse response = customerService.submitCustomer(customerId, request);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/{customerId}/deactivate")
    public ResponseEntity<CustomerResponse> deactivateCustomer(
            @PathVariable Long customerId,
            @Valid @RequestBody ChangeCustomerStatusRequest request
    ) {
        CustomerResponse response = customerService.deactivateCustomer(customerId, request);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/{customerId}/activate")
    public ResponseEntity<CustomerResponse> activateCustomer(
            @PathVariable Long customerId,
            @Valid @RequestBody ChangeCustomerStatusRequest request
    ) {
        CustomerResponse response = customerService.activateCustomer(customerId, request);

        return ResponseEntity.ok(response);
    }

    @PutMapping("/{customerId}/payment-term")
    public ResponseEntity<CustomerResponse> changePaymentTerm(
            @PathVariable Long customerId,
            @Valid @RequestBody ChangeCustomerPaymentTermRequest request
    ) {
        CustomerResponse response = customerService.changePaymentTerm(customerId, request);

        return ResponseEntity.ok(response);
    }
}
