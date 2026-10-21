package com.smartdistribution.masterdata.unit.service.impl;

import com.smartdistribution.masterdata.common.UnitOfMeasure;
import com.smartdistribution.masterdata.unit.dto.response.UnitOfMeasureResponse;
import com.smartdistribution.masterdata.unit.service.UnitOfMeasureService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class UnitOfMeasureServiceImpl implements UnitOfMeasureService {

    @Override
    public List<UnitOfMeasureResponse> findUnits() {
        List<UnitOfMeasureResponse> responses = new ArrayList<>();

        for (UnitOfMeasure unit : UnitOfMeasure.values()) {
            responses.add(
                    new UnitOfMeasureResponse(
                            unit.name(),
                            unit.getDisplayName(),
                            unit.getDecimalPlaces()
                    )
            );
        }

        return responses;
    }
}
