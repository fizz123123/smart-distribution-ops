package com.smartdistribution.masterdata.unit.controller;

import com.smartdistribution.masterdata.unit.dto.response.UnitOfMeasureResponse;
import com.smartdistribution.masterdata.unit.service.UnitOfMeasureService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/units")
@RequiredArgsConstructor
public class UnitOfMeasureController {

    private final UnitOfMeasureService unitOfMeasureService;

    @GetMapping
    public ResponseEntity<List<UnitOfMeasureResponse>> findUnits() {
        List<UnitOfMeasureResponse> response = unitOfMeasureService.findUnits();

        return ResponseEntity.ok(response);
    }
}
