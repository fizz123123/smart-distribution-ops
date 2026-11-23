package com.smartdistribution.pricing.handler;

import com.smartdistribution.approval.entity.ApprovalTargetType;
import com.smartdistribution.approval.handler.ApprovalHandler;
import com.smartdistribution.approval.handler.ApprovalTarget;
import com.smartdistribution.masterdata.companyitem.entity.CompanyItem;
import com.smartdistribution.pricing.entity.PriceStatus;
import com.smartdistribution.pricing.entity.StandardPrice;
import com.smartdistribution.pricing.repository.StandardPriceRepository;
import com.smartdistribution.pricing.service.PricePeriods;
import com.smartdistribution.pricing.service.StandardPriceRules;
import com.smartdistribution.shared.exception.BusinessException;
import com.smartdistribution.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class StandardPriceApprovalHandler implements ApprovalHandler {

    private final StandardPriceRepository standardPriceRepository;
    private final StandardPriceRules standardPriceRules;
    private final Clock clock;

    @Override
    public ApprovalTargetType targetType() {
        return ApprovalTargetType.STANDARD_PRICE;
    }

    //顯示名稱範例：PE-12 標準售價 100.00 元／支 ；待覆核時提示目前價格會在哪一天結束
    @Override
    public ApprovalTarget loadTarget(Long targetId) {
        StandardPrice price = findPrice(targetId);
        String label = price.getCompanyItem().getItemCode() + " 標準售價 " + priceText(price);

        List<String> warnings = new ArrayList<>();
        if (price.getStatus() == PriceStatus.PENDING_REVIEW) {
            LocalDate effectiveFrom = PricePeriods.resolveEffectiveFrom(
                    price.getEffectiveFrom(),
                    LocalDate.now(clock)
            );
            Optional<StandardPrice> latest = findLatestVersion(price);

            if (latest.isPresent()
                    && PricePeriods.stillEffectiveOn(latest.get().getEffectiveTo(), effectiveFrom)) {
                warnings.add("核准後將取代目前價格 " + priceText(latest.get())
                        + "，原價格於 " + effectiveFrom.minusDays(1) + " 結束");
            }
        }

        return new ApprovalTarget(price.getCreatedBy(), label, warnings);
    }

    /**
     * 核准規則和客戶價格相同：決定生效日、不能早於最後一個版本、最後一個版本截到前一天
     */
    @Override
    public void onApproved(Long targetId, Long approverId, Long approvalRequestId) {
        StandardPrice price = findPrice(targetId);

        CompanyItem item = standardPriceRules.lockUsableItem(price.getCompanyItem().getId());
        standardPriceRules.ensurePricingUnitAllowed(item, price.getPricingUnit());

        LocalDate effectiveFrom = PricePeriods.resolveEffectiveFrom(
                price.getEffectiveFrom(),
                LocalDate.now(clock)
        );
        PricePeriods.ensureNotEnded(effectiveFrom, price.getEffectiveTo());

        Optional<StandardPrice> latest = findLatestVersion(price);
        PricePeriods.ensureNotBefore(latest.map(StandardPrice::getEffectiveFrom).orElse(null), effectiveFrom);
        latest.ifPresent(previous -> previous.endBefore(effectiveFrom));

        price.approve(approverId, Instant.now(), effectiveFrom);
    }

    //價格覆核的退回即為拒絕
    @Override
    public void onReturned(Long targetId, Long approverId) {
        findPrice(targetId).reject(approverId, Instant.now());
    }

    private StandardPrice findPrice(Long priceId) {
        return standardPriceRepository.findById(priceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STANDARD_PRICE_NOT_FOUND));
    }

    private Optional<StandardPrice> findLatestVersion(StandardPrice price) {
        return standardPriceRules.findLatestVersion(price.getCompanyItem().getId(), price.getPricingUnit());
    }

    private String priceText(StandardPrice price) {
        return price.getUnitPriceExcludingTax().toPlainString()
                + " 元／" + price.getPricingUnit().getDisplayName();
    }
}
