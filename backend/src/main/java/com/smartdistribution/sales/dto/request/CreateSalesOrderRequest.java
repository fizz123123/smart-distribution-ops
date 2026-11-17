package com.smartdistribution.sales.dto.request;

import com.smartdistribution.sales.entity.DeliveryMethod;
import com.smartdistribution.sales.entity.SalesOrder;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/**
 * 草稿只要求客戶；交貨資料與明細可以之後再補，送審時才檢查是否完整
 */
public record CreateSalesOrderRequest(
        @NotNull
        Long customerId,

        @Size(max = 50)
        String customerOrderNumber,

        //未填時是建立當天；不能是未來日期
        LocalDate orderDate,

        //未指定時是建立者；主管代建時必須指定一位業務
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
        List<@Valid @NotNull SalesOrderLineRequest> lines
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
