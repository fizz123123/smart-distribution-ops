package com.smartdistribution.masterdata.companyitem.controller;

import com.smartdistribution.masterdata.common.MasterDataStatus;
import com.smartdistribution.masterdata.companyitem.dto.request.CreateCompanyItemRequest;
import com.smartdistribution.masterdata.companyitem.dto.request.ReplaceUnitConversionsRequest;
import com.smartdistribution.masterdata.companyitem.dto.request.UpdateCompanyItemRequest;
import com.smartdistribution.masterdata.companyitem.dto.response.CompanyItemDetailResponse;
import com.smartdistribution.masterdata.companyitem.dto.response.CompanyItemSummaryResponse;
import com.smartdistribution.masterdata.companyitem.service.CompanyItemService;
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
@RequestMapping("/api/v1/company-items")
@RequiredArgsConstructor
public class CompanyItemController {

    //公司料號清單：預設依料號
    private static final SortWhitelist COMPANY_ITEM_SORTS = SortWhitelist.builder()
            .field("itemCode", "itemCode")
            .field("name", "name")
            .field("status", "status")
            .field("createdAt", "createdAt")
            .defaultSort(Sort.Direction.ASC, "itemCode")
            .build();

    private final CompanyItemService companyItemService;

    @GetMapping
    public ResponseEntity<PageResponse<CompanyItemSummaryResponse>> findCompanyItems(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) MasterDataStatus status,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        PageResponse<CompanyItemSummaryResponse> pageResponse =
                companyItemService.findCompanyItems(keyword, status, COMPANY_ITEM_SORTS.apply(pageable));

        return ResponseEntity.ok(pageResponse);
    }

    @GetMapping("/{companyItemId}")
    public ResponseEntity<CompanyItemDetailResponse> findCompanyItem(
            @PathVariable Long companyItemId
    ) {
        CompanyItemDetailResponse response = companyItemService.findCompanyItem(companyItemId);

        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<CompanyItemDetailResponse> createCompanyItem(
            @Valid @RequestBody CreateCompanyItemRequest request
    ) {
        CompanyItemDetailResponse response = companyItemService.createCompanyItem(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @PutMapping("/{companyItemId}")
    public ResponseEntity<CompanyItemDetailResponse> updateCompanyItem(
            @PathVariable Long companyItemId,
            @Valid @RequestBody UpdateCompanyItemRequest request
    ) {
        CompanyItemDetailResponse response =
                companyItemService.updateCompanyItem(companyItemId, request);

        return ResponseEntity.ok(response);
    }

    @PutMapping("/{companyItemId}/unit-conversions")
    public ResponseEntity<CompanyItemDetailResponse> replaceUnitConversions(
            @PathVariable Long companyItemId,
            @Valid @RequestBody ReplaceUnitConversionsRequest request
    ) {
        CompanyItemDetailResponse response =
                companyItemService.replaceUnitConversions(companyItemId, request);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/{companyItemId}/submit")
    public ResponseEntity<CompanyItemDetailResponse> submitCompanyItem(
            @PathVariable Long companyItemId,
            @Valid @RequestBody VersionRequest request
    ) {
        CompanyItemDetailResponse response =
                companyItemService.submitCompanyItem(companyItemId, request);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/{companyItemId}/deactivate")
    public ResponseEntity<CompanyItemDetailResponse> deactivateCompanyItem(
            @PathVariable Long companyItemId,
            @Valid @RequestBody ReasonRequest request
    ) {
        CompanyItemDetailResponse response =
                companyItemService.deactivateCompanyItem(companyItemId, request);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/{companyItemId}/activate")
    public ResponseEntity<CompanyItemDetailResponse> activateCompanyItem(
            @PathVariable Long companyItemId,
            @Valid @RequestBody ReasonRequest request
    ) {
        CompanyItemDetailResponse response =
                companyItemService.activateCompanyItem(companyItemId, request);

        return ResponseEntity.ok(response);
    }
}
