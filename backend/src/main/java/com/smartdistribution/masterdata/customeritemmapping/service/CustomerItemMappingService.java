package com.smartdistribution.masterdata.customeritemmapping.service;

import com.smartdistribution.masterdata.common.MasterDataStatus;
import com.smartdistribution.masterdata.customeritemmapping.dto.request.ChangeCustomerItemMappingStatusRequest;
import com.smartdistribution.masterdata.customeritemmapping.dto.request.CreateCustomerItemMappingRequest;
import com.smartdistribution.masterdata.customeritemmapping.dto.request.SubmitCustomerItemMappingRequest;
import com.smartdistribution.masterdata.customeritemmapping.dto.request.UpdateCustomerItemMappingRequest;
import com.smartdistribution.masterdata.customeritemmapping.dto.response.CustomerItemMappingResponse;
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
            SubmitCustomerItemMappingRequest request
    );

    CustomerItemMappingResponse deactivateMapping(
            Long mappingId,
            ChangeCustomerItemMappingStatusRequest request
    );

    CustomerItemMappingResponse activateMapping(
            Long mappingId,
            ChangeCustomerItemMappingStatusRequest request
    );
}
