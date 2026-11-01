package com.smartdistribution.purchasing.service;

import com.smartdistribution.masterdata.common.MasterDataStatus;
import com.smartdistribution.masterdata.common.UnitOfMeasure;
import com.smartdistribution.masterdata.companyitem.entity.CompanyItem;
import com.smartdistribution.purchasing.dto.request.PurchaseOrderLineRequest;
import com.smartdistribution.purchasing.entity.PurchaseOrderLine;
import com.smartdistribution.sales.service.SalesOrderDemandService;
import com.smartdistribution.sales.service.SalesOrderLineDemand;
import com.smartdistribution.shared.exception.BusinessException;
import com.smartdistribution.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * PO 明細的檢查規則，建立、修改草稿與送審共用；錯誤訊息帶行號，採購才知道要修哪一筆
 * 1. 公司料號啟用中；單位是基準單位或已設定換算；數量小數位符合單位
 * 2. 關聯 SO 時：同一筆 SO 明細只出現一次；SO 已核准、料號相同；數量不超過還能規劃的量
 */
@Component
@RequiredArgsConstructor
public class PurchaseOrderLineValidator {

    private final PurchaseOrderMasterData purchaseOrderMasterData;
    private final PurchaseOrderQuantities purchaseOrderQuantities;
    private final SalesOrderDemandService salesOrderDemandService;

    /**
     * @param excludedPurchaseOrderId 修改或送審某張 PO 時排除它自己，否則會被自己原本的規劃量擋下；
     *                                建立時傳 {@link PurchaseOrderQuantities#NO_EXCLUDED_PURCHASE_ORDER}
     * @return 檢查通過的明細，各自帶著當下的換算倍率，順序和輸入相同
     */
    public List<CheckedLine> validate(List<LineInput> lines, Long excludedPurchaseOrderId) {
        List<CheckedLine> checkedLines = new ArrayList<>();
        Set<Long> salesOrderLineIds = new HashSet<>();

        for (LineInput line : lines) {
            checkedLines.add(new CheckedLine(line, checkItemAndUnit(line)));

            if (line.salesOrderLineId() != null && !salesOrderLineIds.add(line.salesOrderLineId())) {
                throw new BusinessException(
                        ErrorCode.PO_LINE_INVALID,
                        linePrefix(line) + "同一筆 SO 明細只能出現一次。"
                );
            }
        }

        checkSources(checkedLines, salesOrderLineIds, excludedPurchaseOrderId);

        return checkedLines;
    }

    //料號要啟用、單位要能換算、小數位要符合單位；回傳這個單位的換算倍率
    private BigDecimal checkItemAndUnit(LineInput line) {
        CompanyItem item = purchaseOrderMasterData.findCompanyItem(line.companyItemId());
        String itemPrefix = "第 " + line.lineNo() + " 行 " + item.getItemCode() + "：";

        if (item.getStatus() != MasterDataStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.PO_ITEM_INACTIVE, itemPrefix + "公司料號尚未啟用。");
        }

        BigDecimal factor = purchaseOrderMasterData.findConversionFactor(item, line.unit())
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.PO_UNIT_INVALID,
                        itemPrefix + "公司料號沒有設定「" + line.unit().getDisplayName() + "」的換算。"
                ));

        int allowedScale = line.unit().getDecimalPlaces();
        if (line.quantity().stripTrailingZeros().scale() > allowedScale) {
            throw new BusinessException(
                    ErrorCode.PO_LINE_INVALID,
                    linePrefix(line) + "「" + line.unit().getDisplayName() + "」最多 " + allowedScale + " 位小數。"
            );
        }

        return factor;
    }

    //關聯 SO 的明細：來源要是已核准、料號相同的 SO 明細，數量不能超過還能規劃的量
    private void checkSources(
            List<CheckedLine> checkedLines,
            Set<Long> salesOrderLineIds,
            Long excludedPurchaseOrderId
    ) {
        if (salesOrderLineIds.isEmpty()) {
            return;
        }

        Map<Long, SalesOrderLineDemand> demands = salesOrderDemandService.findDemands(salesOrderLineIds);
        Map<Long, BigDecimal> plannable = purchaseOrderQuantities.findPlannableQty(demands, excludedPurchaseOrderId);

        for (CheckedLine checkedLine : checkedLines) {
            LineInput line = checkedLine.input();
            if (line.salesOrderLineId() == null) {
                continue;
            }

            SalesOrderLineDemand demand = demands.get(line.salesOrderLineId());
            if (demand == null || !demand.approved() || !Objects.equals(demand.companyItemId(), line.companyItemId())) {
                throw new BusinessException(
                        ErrorCode.PO_SOURCE_INVALID,
                        linePrefix(line) + "來源 SO 明細必須屬於已核准的 SO，且料號要和這一行相同。"
                );
            }

            BigDecimal maxQty = plannable.get(line.salesOrderLineId());
            if (checkedLine.baseQty().compareTo(maxQty) > 0) {
                throw new BusinessException(
                        ErrorCode.PO_SOURCE_QUANTITY_EXCEEDED,
                        linePrefix(line) + "來源 %s 第 %d 行最多還能規劃 %s（庫存基準單位）；要多備貨請另列一筆一般補庫。".formatted(
                                demand.salesOrderNumber(),
                                demand.lineNo(),
                                maxQty.stripTrailingZeros().toPlainString()
                        )
                );
            }
        }
    }

    private static String linePrefix(LineInput line) {
        return "第 " + line.lineNo() + " 行：";
    }

    /**
     * 檢查需要的明細欄位；建立與修改時來自 request，送審時來自已存的明細，兩邊共用同一套檢查
     */
    public record LineInput(
            int lineNo,
            Long companyItemId,
            Long salesOrderLineId,
            BigDecimal quantity,
            UnitOfMeasure unit,
            BigDecimal unitPriceExcludingTax
    ) {

        //行號依 request 的順序從 1 開始
        public static List<LineInput> fromRequests(List<PurchaseOrderLineRequest> requests) {
            List<LineInput> inputs = new ArrayList<>();

            for (int index = 0; index < requests.size(); index++) {
                PurchaseOrderLineRequest request = requests.get(index);
                inputs.add(new LineInput(
                        index + 1,
                        request.companyItemId(),
                        request.salesOrderLineId(),
                        request.quantity(),
                        request.unit(),
                        request.unitPriceExcludingTax()
                ));
            }

            return inputs;
        }

        public static LineInput from(PurchaseOrderLine line) {
            return new LineInput(
                    line.getLineNo(),
                    line.getCompanyItemId(),
                    line.getSalesOrderLineId(),
                    line.getQuantity(),
                    line.getUnit(),
                    line.getUnitPriceExcludingTax()
            );
        }
    }

    /**
     * 檢查通過的明細，帶著當下的換算倍率
     */
    public record CheckedLine(
            LineInput input,
            BigDecimal conversionFactor
    ) {

        public BigDecimal baseQty() {
            return PurchaseOrderAmounts.baseQty(input.quantity(), conversionFactor);
        }
    }
}
