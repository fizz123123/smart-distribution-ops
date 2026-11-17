package com.smartdistribution.sales.dto.request;

import com.smartdistribution.sales.entity.DeliveryMethod;
import com.smartdistribution.sales.entity.SalesOrder;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/**
 * 整份送出：可為空的欄位以 null 表示清空，明細整批取代
 */
public record UpdateSalesOrderRequest(
        @NotNull
        Long customerId,

        @Size(max = 50)
        String customerOrderNumber,

        //整份送出，畫面會帶出目前的訂單日期，所以必填
        @NotNull
        LocalDate orderDate,

        Long responsibleSalesUserId,

        LocalDate requestedDeliveryDate,

        DeliveryMethod deliveryMethod,

        @Size(max = 200)
        String deliveryAddress,

        @Size(max = 50)
        String contactName,

        @Size(max = 30)
        String contactPhone,

        @Size(max = 500)
        String remark,

        @NotNull
        @Size(max = 100)
        List<@Valid @NotNull SalesOrderLineRequest> lines,

        @NotNull
        @PositiveOrZero
        Long version
) {

    /**
     * 組成草稿欄位
     *
     * @param resolvedOrderDate service 補上預設值並檢查過的訂單日期
     * @return 草稿欄位
     */
    public SalesOrder.DraftFields toDraftFields(LocalDate resolvedOrderDate) {
        return new SalesOrder.DraftFields(
                customerOrderNumber,
                resolvedOrderDate,
                requestedDeliveryDate,
                deliveryMethod,
                deliveryAddress,
                contactName,
                contactPhone,
                remark
        );
    }
}
