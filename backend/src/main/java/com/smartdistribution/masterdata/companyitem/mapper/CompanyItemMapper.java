package com.smartdistribution.masterdata.companyitem.mapper;

import com.smartdistribution.inventory.dto.response.InventoryPolicyResponse;
import com.smartdistribution.masterdata.companyitem.dto.response.CompanyItemDetailResponse;
import com.smartdistribution.masterdata.companyitem.dto.response.CompanyItemSummaryResponse;
import com.smartdistribution.masterdata.companyitem.dto.response.PreferredSupplierResponse;
import com.smartdistribution.masterdata.companyitem.dto.response.UnitConversionResponse;
import com.smartdistribution.masterdata.companyitem.entity.CompanyItem;
import com.smartdistribution.masterdata.companyitem.entity.ItemUnitConversion;
import com.smartdistribution.masterdata.supplier.entity.Supplier;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Component
public class CompanyItemMapper {

    public CompanyItemSummaryResponse toSummary(CompanyItem item) {
        return new CompanyItemSummaryResponse(
                item.getId(),
                item.getItemCode(),
                item.getName(),
                item.getBaseUnit(),
                item.getPreferredSupplierId(),
                item.getStatus(),
                item.getVersion()
        );
    }

    public CompanyItemDetailResponse toDetail(
            CompanyItem item,
            Supplier preferredSupplier,
            List<ItemUnitConversion> conversions,
            InventoryPolicyResponse inventoryPolicy
    ) {
        return new CompanyItemDetailResponse(
                item.getId(),
                item.getItemCode(),
                item.getName(),
                item.getBaseUnit(),
                toPreferredSupplier(preferredSupplier),
                item.getStatus(),
                item.getCreatedBy(),
                item.getCreatedAt(),
                item.getUpdatedAt(),
                item.getVersion(),
                toUnitConversions(item, conversions),
                inventoryPolicy
        );
    }

    private PreferredSupplierResponse toPreferredSupplier(Supplier supplier) {
        if (supplier == null) {
            return null;
        }

        return new PreferredSupplierResponse(
                supplier.getId(),
                supplier.getSupplierCode(),
                supplier.getName(),
                supplier.getStatus()
        );
    }

    //基準單位不另存換算列，回應時補在最前面，倍率固定為 1
    private List<UnitConversionResponse> toUnitConversions(
            CompanyItem item,
            List<ItemUnitConversion> conversions
    ) {
        List<UnitConversionResponse> responses = new ArrayList<>();
        responses.add(new UnitConversionResponse(item.getBaseUnit(), BigDecimal.ONE, true));

        for (ItemUnitConversion conversion : conversions) {
            responses.add(new UnitConversionResponse(
                    conversion.getUnit(),
                    conversion.getFactor(),
                    false
            ));
        }

        return responses;
    }
}
