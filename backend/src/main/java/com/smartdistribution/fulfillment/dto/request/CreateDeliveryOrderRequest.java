package com.smartdistribution.fulfillment.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/**
 * 業務通知出貨：只列這次要出的明細與數量；分批出貨就是分次通知
 */
public record CreateDeliveryOrderRequest(
        //業務依客戶需求填寫
        @NotNull
        LocalDate plannedShipmentDate,

        @Size(max = 500)
        String remark,

        @NotEmpty
        @Size(max = 100)
        List<@Valid @NotNull DeliveryOrderLineRequest> lines
) {
}
