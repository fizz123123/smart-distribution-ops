package com.smartdistribution.masterdata.unit.service;

import com.smartdistribution.masterdata.unit.dto.response.UnitOfMeasureResponse;

import java.util.List;

public interface UnitOfMeasureService {

    List<UnitOfMeasureResponse> findUnits();
}
