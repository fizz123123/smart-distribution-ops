package com.smartdistribution.pricing.handler;

import com.smartdistribution.approval.entity.ApprovalTargetType;
import com.smartdistribution.approval.handler.ApprovalHandler;
import com.smartdistribution.approval.handler.ApprovalTarget;
import com.smartdistribution.masterdata.customeritemmapping.entity.CustomerItemMapping;
import com.smartdistribution.pricing.entity.CustomerPrice;
import com.smartdistribution.pricing.entity.PriceStatus;
import com.smartdistribution.pricing.repository.CustomerPriceRepository;
import com.smartdistribution.pricing.service.CustomerPriceRules;
import com.smartdistribution.pricing.service.PricePeriods;
import com.smartdistribution.shared.exception.BusinessException;
import com.smartdistribution.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

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

    @Override
    public ApprovalTargetType targetType() {
        return ApprovalTargetType.CUSTOMER_PRICE;
    }

    //顯示名稱範例：CUS-000003 A-100 125.00 元／個 ；待覆核時提示目前價格會在哪一天結束
    @Override
    public ApprovalTarget loadTarget(Long targetId) {
        CustomerPrice price = findPrice(targetId);
        CustomerItemMapping mapping = price.getCustomerItemMapping();

        String label = mapping.getCustomer().getCustomerCode() + " "
                + mapping.getCustomerItemCode() + " "
                + priceText(price);

        List<String> warnings = new ArrayList<>();
        if (price.getStatus() == PriceStatus.PENDING_REVIEW) {
            LocalDate effectiveFrom = PricePeriods.resolveEffectiveFrom(price.getEffectiveFrom());
            Optional<CustomerPrice> latest = findLatestVersion(price);

            if (latest.isPresent() && overlaps(latest.get(), effectiveFrom)) {
                warnings.add("核准後將取代目前價格 " + priceText(latest.get())
                        + "，原價格於 " + effectiveFrom.minusDays(1) + " 結束");
            }
        }

        return new ApprovalTarget(price.getCreatedBy(), label, warnings);
    }

    /**
     * 核准：
     * 1. 決定生效日：沒填或已經過了就是今天，價格不溯及既往
     * 2. 生效日不能早於最後一個版本的生效日
     * 3. 最後一個版本截到新生效日的前一天，轉 REPLACED
     */
    @Override
    public void onApproved(Long targetId, Long approverId, Long approvalRequestId) {
        CustomerPrice price = findPrice(targetId);

        //先鎖對應，同一對應的價格核准依序進行，最後一個版本才不會被兩筆同時截斷
        CustomerItemMapping mapping =
                customerPriceRules.lockUsableMapping(price.getCustomerItemMapping().getId());
        customerPriceRules.ensurePricingUnitAllowed(mapping, price.getPricingUnit());

        LocalDate effectiveFrom = PricePeriods.resolveEffectiveFrom(price.getEffectiveFrom());
        PricePeriods.ensureNotEnded(effectiveFrom, price.getEffectiveTo());

        Optional<CustomerPrice> latest = findLatestVersion(price);
        customerPriceRules.ensureNotBeforeLatest(latest, effectiveFrom);
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

    private Optional<CustomerPrice> findLatestVersion(CustomerPrice price) {
        return customerPriceRules.findLatestVersion(
                price.getCustomerItemMapping().getId(),
                price.getPricingUnit()
        );
    }

    //最後一個版本在新生效日當天或之後仍然有效，核准後會被截短
    private boolean overlaps(CustomerPrice latest, LocalDate effectiveFrom) {
        return latest.getEffectiveTo() == null || !latest.getEffectiveTo().isBefore(effectiveFrom);
    }

    private String priceText(CustomerPrice price) {
        return price.getUnitPriceExcludingTax().toPlainString()
                + " 元／" + price.getPricingUnit().getDisplayName();
    }
}
