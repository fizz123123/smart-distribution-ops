package com.smartdistribution.pricing.controller;

import com.smartdistribution.masterdata.common.UnitOfMeasure;
import com.smartdistribution.pricing.dto.response.ApplicablePriceResponse;
import com.smartdistribution.pricing.service.ApplicablePriceService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/prices")
@RequiredArgsConstructor
public class ApplicablePriceController {

    private final ApplicablePriceService applicablePriceService;

    //建立 SO 時預填價格：客戶料號＋訂購單位＋訂單日期
    @GetMapping("/applicable")
    public ResponseEntity<ApplicablePriceResponse> findApplicablePrice(
            @RequestParam Long customerItemMappingId,
            @RequestParam UnitOfMeasure unit,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return ResponseEntity.ok(applicablePriceService.findApplicablePrice(customerItemMappingId, unit, date));
    }
}
