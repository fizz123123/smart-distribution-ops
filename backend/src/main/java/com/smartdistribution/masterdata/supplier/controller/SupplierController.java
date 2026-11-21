package com.smartdistribution.masterdata.supplier.controller;

import com.smartdistribution.masterdata.common.MasterDataStatus;
import com.smartdistribution.masterdata.supplier.dto.request.CreateSupplierRequest;
import com.smartdistribution.masterdata.supplier.dto.request.UpdateSupplierRequest;
import com.smartdistribution.masterdata.supplier.dto.response.SupplierResponse;
import com.smartdistribution.masterdata.supplier.service.SupplierService;
import com.smartdistribution.shared.web.request.ReasonRequest;
import com.smartdistribution.shared.web.request.VersionRequest;
import com.smartdistribution.shared.web.response.PageResponse;
import com.smartdistribution.shared.web.sort.SortWhitelist;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/suppliers")
@RequiredArgsConstructor
public class SupplierController {

    //供應商清單：預設依編號
    private static final SortWhitelist SUPPLIER_SORTS = SortWhitelist.builder()
            .field("supplierCode", "supplierCode")
            .field("name", "name")
            .field("status", "status")
            .field("createdAt", "createdAt")
            .defaultSort(Sort.Direction.ASC, "supplierCode")
            .build();

    private final SupplierService supplierService;

    @GetMapping
    public ResponseEntity<PageResponse<SupplierResponse>> findSuppliers(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) MasterDataStatus status,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        PageResponse<SupplierResponse> pageResponse =
                supplierService.findSuppliers(keyword, status, SUPPLIER_SORTS.apply(pageable));

        return ResponseEntity.ok(pageResponse);
    }

    @GetMapping("/{supplierId}")
    public ResponseEntity<SupplierResponse> findSupplier(@PathVariable Long supplierId) {
        SupplierResponse response = supplierService.findSupplier(supplierId);
        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<SupplierResponse> createSupplier(
            @Valid @RequestBody CreateSupplierRequest request
    ) {
        SupplierResponse response = supplierService.createSupplier(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @PutMapping("/{supplierId}")
    public ResponseEntity<SupplierResponse> updateSupplier(
            @PathVariable Long supplierId,
            @Valid @RequestBody UpdateSupplierRequest request
    ) {
        SupplierResponse response = supplierService.updateSupplier(supplierId, request);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/{supplierId}/submit")
    public ResponseEntity<SupplierResponse> submitSupplier(
            @PathVariable Long supplierId,
            @Valid @RequestBody VersionRequest request
    ) {
        SupplierResponse response = supplierService.submitSupplier(supplierId, request);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/{supplierId}/deactivate")
    public ResponseEntity<SupplierResponse> deactivateSupplier(
            @PathVariable Long supplierId,
            @Valid @RequestBody ReasonRequest request
    ) {
        SupplierResponse response = supplierService.deactivateSupplier(supplierId, request);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/{supplierId}/activate")
    public ResponseEntity<SupplierResponse> activateSupplier(
            @PathVariable Long supplierId,
            @Valid @RequestBody ReasonRequest request
    ) {
        SupplierResponse response = supplierService.activateSupplier(supplierId, request);

        return ResponseEntity.ok(response);
    }
}
