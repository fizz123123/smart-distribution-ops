package com.smartdistribution.receivable.service;

import com.smartdistribution.receivable.entity.Receivable;
import com.smartdistribution.receivable.repository.PaidAmountProjection;
import com.smartdistribution.receivable.repository.PaymentAllocationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 應收的已收金額與未收餘額，不保存，每次由核銷明細加總：
 * 已收金額 ＝ CONFIRMED 收款的核銷加總
 * 未收餘額 ＝ 含稅總額 － 已收金額
 * 收款作廢後不必回頭改任何數字，重新加總就恢復了
 */
@Component
@RequiredArgsConstructor
public class ReceivableBalances {

    private final PaymentAllocationRepository paymentAllocationRepository;

    //應收 ID → 已收金額；還沒有收款的應收不會出現在結果中
    public Map<Long, BigDecimal> findPaidAmounts(Collection<Long> receivableIds) {
        if (receivableIds.isEmpty()) {
            return Map.of();
        }

        return paymentAllocationRepository.sumPaidByReceivableIds(receivableIds)
                .stream()
                .collect(Collectors.toMap(PaidAmountProjection::receivableId, PaidAmountProjection::paidAmount));
    }

    public static BigDecimal paidAmount(Receivable receivable, Map<Long, BigDecimal> paidAmounts) {
        return paidAmounts.getOrDefault(receivable.getId(), BigDecimal.ZERO);
    }

    public static BigDecimal balance(Receivable receivable, Map<Long, BigDecimal> paidAmounts) {
        return receivable.getTotalAmount().subtract(paidAmount(receivable, paidAmounts));
    }
}
