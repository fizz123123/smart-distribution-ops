package com.smartdistribution.masterdata.customeritemmapping.service;

import com.smartdistribution.masterdata.common.MasterDataStatus;
import com.smartdistribution.masterdata.customeritemmapping.dto.request.CreateCustomerItemMappingRequest;
import com.smartdistribution.masterdata.customeritemmapping.dto.request.UpdateCustomerItemMappingRequest;
import com.smartdistribution.masterdata.customeritemmapping.dto.response.CustomerItemMappingResponse;
import com.smartdistribution.shared.web.request.ReasonRequest;
import com.smartdistribution.shared.web.request.VersionRequest;
import com.smartdistribution.shared.web.response.PageResponse;
import org.springframework.data.domain.Pageable;

public interface CustomerItemMappingService {

    PageResponse<CustomerItemMappingResponse> findMappings(
            Long customerId,
            String keyword,
            MasterDataStatus status,
            Pageable pageable
    );

    CustomerItemMappingResponse findMapping(Long mappingId);

    CustomerItemMappingResponse createMapping(CreateCustomerItemMappingRequest request);

    CustomerItemMappingResponse updateMapping(
            Long mappingId,
            UpdateCustomerItemMappingRequest request
    );

    CustomerItemMappingResponse submitMapping(
            Long mappingId,
            VersionRequest request
    );

    CustomerItemMappingResponse deactivateMapping(
            Long mappingId,
            ReasonRequest request
    );

    CustomerItemMappingResponse activateMapping(
            Long mappingId,
            ReasonRequest request
    );
}
