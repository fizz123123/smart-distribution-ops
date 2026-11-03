package com.smartdistribution.masterdata.companyitem.service;

import com.smartdistribution.masterdata.common.MasterDataStatus;
import com.smartdistribution.masterdata.companyitem.dto.request.CreateCompanyItemRequest;
import com.smartdistribution.masterdata.companyitem.dto.request.ReplaceUnitConversionsRequest;
import com.smartdistribution.masterdata.companyitem.dto.request.UpdateCompanyItemRequest;
import com.smartdistribution.masterdata.companyitem.dto.response.CompanyItemDetailResponse;
import com.smartdistribution.masterdata.companyitem.dto.response.CompanyItemSummaryResponse;
import com.smartdistribution.shared.web.request.ReasonRequest;
import com.smartdistribution.shared.web.request.VersionRequest;
import com.smartdistribution.shared.web.response.PageResponse;
import org.springframework.data.domain.Pageable;

public interface CompanyItemService {

    PageResponse<CompanyItemSummaryResponse> findCompanyItems(
            String keyword,
            MasterDataStatus status,
            Pageable pageable
    );

    CompanyItemDetailResponse findCompanyItem(Long companyItemId);

    CompanyItemDetailResponse createCompanyItem(CreateCompanyItemRequest request);

    CompanyItemDetailResponse updateCompanyItem(
            Long companyItemId,
            UpdateCompanyItemRequest request
    );

    CompanyItemDetailResponse replaceUnitConversions(
            Long companyItemId,
            ReplaceUnitConversionsRequest request
    );

    CompanyItemDetailResponse submitCompanyItem(
            Long companyItemId,
            VersionRequest request
    );

    CompanyItemDetailResponse deactivateCompanyItem(
            Long companyItemId,
            ReasonRequest request
    );

    CompanyItemDetailResponse activateCompanyItem(
            Long companyItemId,
            ReasonRequest request
    );
}
