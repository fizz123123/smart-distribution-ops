package com.smartdistribution.masterdata.customerprice.handler;

import com.smartdistribution.approval.entity.ApprovalTargetType;
import com.smartdistribution.approval.handler.ApprovalHandler;
import com.smartdistribution.approval.handler.ApprovalTarget;
import com.smartdistribution.masterdata.customeritemmapping.entity.CustomerItemMapping;
import com.smartdistribution.masterdata.customerprice.entity.CustomerPrice;
import com.smartdistribution.masterdata.customerprice.entity.CustomerPriceStatus;
import com.smartdistribution.masterdata.customerprice.repository.CustomerPriceRepository;
import com.smartdistribution.masterdata.customerprice.service.CustomerPriceRules;
import com.smartdistribution.shared.exception.BusinessException;
import com.smartdistribution.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
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

    //顯示名稱範例：CUS-000003 A-100 125.00 元／個 ；待覆核時以警示顯示將被取代的現行價格
    @Override
    public ApprovalTarget loadTarget(Long targetId) {
        CustomerPrice price = findPrice(targetId);
        CustomerItemMapping mapping = price.getCustomerItemMapping();

        String label = mapping.getCustomer().getCustomerCode() + " "
                + mapping.getCustomerItemCode() + " "
                + priceText(price);

        List<String> warnings = new ArrayList<>();
        if (price.getStatus() == CustomerPriceStatus.PENDING_REVIEW) {
            Optional<CustomerPrice> currentPrice = findActivePrice(price);

            if (currentPrice.isPresent()) {
                warnings.add("核准後將取代目前價格 " + priceText(currentPrice.get()));
            }
        }

        return new ApprovalTarget(price.getCreatedBy(), label, warnings);
    }

    //核准後新價格立即生效，舊價格在同一交易中轉為 REPLACED
    @Override
    public void onApproved(Long targetId, Long approverId, Long approvalRequestId) {
        CustomerPrice price = findPrice(targetId);

        CustomerItemMapping mapping =
                customerPriceRules.lockUsableMapping(price.getCustomerItemMapping().getId());
        customerPriceRules.ensurePricingUnitAllowed(mapping, price.getPricingUnit());

        Optional<CustomerPrice> currentPrice = findActivePrice(price);

        if (currentPrice.isPresent()) {
            currentPrice.get().replace();
        }

        price.approve(approverId, Instant.now());
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

    private Optional<CustomerPrice> findActivePrice(CustomerPrice price) {
        return customerPriceRepository.findByCustomerItemMappingIdAndPricingUnitAndStatus(
                price.getCustomerItemMapping().getId(),
                price.getPricingUnit(),
                CustomerPriceStatus.ACTIVE
        );
    }

    private String priceText(CustomerPrice price) {
        return price.getUnitPriceExcludingTax().toPlainString()
                + " 元／" + price.getPricingUnit().getDisplayName();
    }
}
