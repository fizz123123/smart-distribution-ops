package com.smartdistribution.masterdata.companyitem.service;

import com.smartdistribution.masterdata.common.MasterDataStatus;
import com.smartdistribution.masterdata.companyitem.dto.request.ChangeCompanyItemStatusRequest;
import com.smartdistribution.masterdata.companyitem.dto.request.CreateCompanyItemRequest;
import com.smartdistribution.masterdata.companyitem.dto.request.ReplaceUnitConversionsRequest;
import com.smartdistribution.masterdata.companyitem.dto.request.SubmitCompanyItemRequest;
import com.smartdistribution.masterdata.companyitem.dto.request.UpdateCompanyItemRequest;
import com.smartdistribution.masterdata.companyitem.dto.response.CompanyItemDetailResponse;
import com.smartdistribution.masterdata.companyitem.dto.response.CompanyItemSummaryResponse;
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
            SubmitCompanyItemRequest request
    );

    CompanyItemDetailResponse deactivateCompanyItem(
            Long companyItemId,
            ChangeCompanyItemStatusRequest request
    );

    CompanyItemDetailResponse activateCompanyItem(
            Long companyItemId,
            ChangeCompanyItemStatusRequest request
    );
}
