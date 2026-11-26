package com.smartdistribution.pricing.handler;

import com.smartdistribution.approval.entity.ApprovalTargetType;
import com.smartdistribution.approval.handler.ApprovalHandler;
import com.smartdistribution.approval.handler.ApprovalTarget;
import com.smartdistribution.masterdata.customeritemmapping.entity.CustomerItemMapping;
import com.smartdistribution.pricing.entity.CustomerPrice;
import com.smartdistribution.pricing.entity.PriceStatus;
import com.smartdistribution.pricing.entity.StandardPrice;
import com.smartdistribution.pricing.repository.CustomerPriceRepository;
import com.smartdistribution.pricing.repository.StandardPriceRepository;
import com.smartdistribution.pricing.service.CustomerPriceRules;
import com.smartdistribution.pricing.service.PricePeriods;
import com.smartdistribution.shared.exception.BusinessException;
import com.smartdistribution.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class CustomerPriceApprovalHandler implements ApprovalHandler {

    private final CustomerPriceRepository customerPriceRepository;
    private final CustomerPriceRules customerPriceRules;
    private final StandardPriceRepository standardPriceRepository;
    private final Clock clock;

    @Override
    public ApprovalTargetType targetType() {
        return ApprovalTargetType.CUSTOMER_PRICE;
    }

    //顯示名稱範例：CUS-000003 A-100 125.00 元／個
    //待覆核時提示：核准後從哪天生效、是否已超過截止日、目前價格會在哪一天結束、和生效日的標準售價差多少
    @Override
    public ApprovalTarget loadTarget(Long targetId) {
        CustomerPrice price = findPrice(targetId);
        CustomerItemMapping mapping = price.getCustomerItemMapping();

        String label = mapping.getCustomer().getCustomerCode() + " "
                + mapping.getCustomerItemCode() + " "
                + priceText(price);

        List<String> warnings = new ArrayList<>();
        if (price.getStatus() == PriceStatus.PENDING_REVIEW) {
            LocalDate today = LocalDate.now(clock);
            warnings.addAll(PricePeriods.approvalNotices(price.getEffectiveFrom(), price.getEffectiveTo(), today));

            LocalDate effectiveFrom = PricePeriods.resolveEffectiveFrom(price.getEffectiveFrom(), today);
            Optional<CustomerPrice> latest = findLatestVersion(price);

            if (latest.isPresent()
                    && PricePeriods.stillEffectiveOn(latest.get().getEffectiveTo(), effectiveFrom)) {
                warnings.add("核准後將取代目前價格 " + priceText(latest.get())
                        + "，原價格於 " + effectiveFrom.minusDays(1) + " 結束");
            }

            compareWithStandardPrice(price, effectiveFrom).ifPresent(warnings::add);
        }

        return new ApprovalTarget(price.getCreatedBy(), label, warnings);
    }

    /**
     * 核准：
     * 1. 決定生效日：沒填或已經過了就是今天，價格不溯及既往
     * 2. 生效日必須晚於最後一個版本的生效日
     * 3. 最後一個版本截到新生效日的前一天，轉 REPLACED
     */
    @Override
    public void onApproved(Long targetId, Long approverId, Long approvalRequestId) {
        CustomerPrice price = findPrice(targetId);

        //先鎖對應，同一對應的價格核准依序進行，最後一個版本才不會被兩筆同時截斷
        CustomerItemMapping mapping =
                customerPriceRules.lockUsableMapping(price.getCustomerItemMapping().getId());
        customerPriceRules.ensurePricingUnitAllowed(mapping, price.getPricingUnit());

        LocalDate effectiveFrom = PricePeriods.resolveEffectiveFrom(
                price.getEffectiveFrom(),
                LocalDate.now(clock)
        );
        PricePeriods.ensureNotEndedOnApproval(effectiveFrom, price.getEffectiveTo());

        Optional<CustomerPrice> latest = findLatestVersion(price);
        PricePeriods.ensureAfterLatest(latest.map(CustomerPrice::getEffectiveFrom).orElse(null), effectiveFrom);
        latest.ifPresent(previous -> previous.endBefore(effectiveFrom));

        price.approve(approverId, Instant.now(), effectiveFrom);
    }

    //價格覆核的退回即為拒絕
    @Override
    public void onReturned(Long targetId, Long approverId) {
        findPrice(targetId).reject(approverId, Instant.now());
    }

    private CustomerPrice findPrice(Long priceId) {
        return customerPriceRepository.findById(priceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CUSTOMER_PRICE_NOT_FOUND));
    }

    //客戶價格是針對單一客戶的議價，主管核准時要知道它和公開的標準售價差多少
    private Optional<String> compareWithStandardPrice(CustomerPrice price, LocalDate effectiveFrom) {
        Optional<StandardPrice> standardPrice = standardPriceRepository.findEffectiveOn(
                price.getCustomerItemMapping().getCompanyItem().getId(),
                price.getPricingUnit(),
                effectiveFrom,
                PriceStatus.APPROVED_VERSIONS
        );

        if (standardPrice.isEmpty()) {
            return Optional.empty();
        }

        BigDecimal standard = standardPrice.get().getUnitPriceExcludingTax();
        BigDecimal difference = price.getUnitPriceExcludingTax().subtract(standard);
        String comparison = switch (difference.signum()) {
            case 0 -> "相同";
            case 1 -> "高 " + difference.toPlainString() + " 元";
            default -> "低 " + difference.negate().toPlainString() + " 元";
        };

        return Optional.of("生效日的標準售價為 " + standard.toPlainString() + " 元／"
                + price.getPricingUnit().getDisplayName() + "，此客戶價格" + comparison);
    }

    private Optional<CustomerPrice> findLatestVersion(CustomerPrice price) {
        return customerPriceRules.findLatestVersion(
                price.getCustomerItemMapping().getId(),
                price.getPricingUnit()
        );
    }

    private String priceText(CustomerPrice price) {
        return price.getUnitPriceExcludingTax().toPlainString()
                + " 元／" + price.getPricingUnit().getDisplayName();
    }
}
