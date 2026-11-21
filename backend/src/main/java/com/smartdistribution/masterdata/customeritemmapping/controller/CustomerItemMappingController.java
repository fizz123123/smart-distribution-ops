package com.smartdistribution.masterdata.customeritemmapping.controller;

import com.smartdistribution.masterdata.common.MasterDataStatus;
import com.smartdistribution.masterdata.customeritemmapping.dto.request.CreateCustomerItemMappingRequest;
import com.smartdistribution.masterdata.customeritemmapping.dto.request.UpdateCustomerItemMappingRequest;
import com.smartdistribution.masterdata.customeritemmapping.dto.response.CustomerItemMappingResponse;
import com.smartdistribution.masterdata.customeritemmapping.service.CustomerItemMappingService;
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
@RequestMapping("/api/v1/customer-item-mappings")
@RequiredArgsConstructor
public class CustomerItemMappingController {

    //客戶料號對應清單：預設依客戶編號、客戶料號；客戶與公司料號都在主檔模組，可以直接用關聯排序
    private static final SortWhitelist MAPPING_SORTS = SortWhitelist.builder()
            .field("customerCode", "customer.customerCode")
            .field("customerItemCode", "customerItemCode")
            .field("itemCode", "companyItem.itemCode")
            .field("status", "status")
            .defaultSort(Sort.Direction.ASC, "customerCode", "customerItemCode")
            .build();

    private final CustomerItemMappingService customerItemMappingService;

    @GetMapping
    public ResponseEntity<PageResponse<CustomerItemMappingResponse>> findMappings(
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) MasterDataStatus status,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        PageResponse<CustomerItemMappingResponse> pageResponse =
                customerItemMappingService.findMappings(customerId, keyword, status, MAPPING_SORTS.apply(pageable));

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

    @PutMapping("/{mappingId}")
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
            @Valid @RequestBody VersionRequest request
    ) {
        CustomerItemMappingResponse response =
                customerItemMappingService.submitMapping(mappingId, request);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/{mappingId}/deactivate")
    public ResponseEntity<CustomerItemMappingResponse> deactivateMapping(
            @PathVariable Long mappingId,
            @Valid @RequestBody ReasonRequest request
    ) {
        CustomerItemMappingResponse response =
                customerItemMappingService.deactivateMapping(mappingId, request);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/{mappingId}/activate")
    public ResponseEntity<CustomerItemMappingResponse> activateMapping(
            @PathVariable Long mappingId,
            @Valid @RequestBody ReasonRequest request
    ) {
        CustomerItemMappingResponse response =
                customerItemMappingService.activateMapping(mappingId, request);

        return ResponseEntity.ok(response);
    }
}
