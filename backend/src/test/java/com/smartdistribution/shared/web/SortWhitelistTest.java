package com.smartdistribution.shared.web;

import com.smartdistribution.shared.exception.BusinessException;
import com.smartdistribution.shared.web.sort.SortWhitelist;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/**
 * 不需要啟動 Spring：直接檢查排序參數怎麼被換成查詢用的排序
 */
class SortWhitelistTest {

    private final SortWhitelist whitelist = SortWhitelist.builder()
            .field("orderDate", "orderDate")
            .field("itemCode", "item.itemCode")
            .expression("availableQty", "balance.onHandQty - balance.reservedQty")
            .defaultSort(Sort.Direction.DESC, "orderDate")
            .build();

    @Test
    void defaultSortIsUsedWhenNoSortIsRequestedAndIdBreaksTies() {
        Pageable pageable = whitelist.apply(PageRequest.of(2, 20));

        assertThat(pageable.getPageNumber()).isEqualTo(2);
        assertThat(pageable.getPageSize()).isEqualTo(20);
        assertThat(pageable.getSort().toList())
                .extracting(Sort.Order::getProperty, Sort.Order::getDirection)
                .containsExactly(
                        tuple("orderDate", Sort.Direction.DESC),
                        tuple("id", Sort.Direction.DESC)
                );
    }

    @Test
    void requestedFieldIsMappedToQueryPath() {
        Pageable pageable = whitelist.apply(PageRequest.of(0, 20, Sort.by(Sort.Direction.ASC, "itemCode")));

        assertThat(pageable.getSort().toList())
                .extracting(Sort.Order::getProperty)
                .containsExactly("item.itemCode", "id");
        assertThat(pageable.getSort().getOrderFor("id").getDirection()).isEqualTo(Sort.Direction.ASC);
    }

    @Test
    void expressionCanBeUsedForCalculatedValues() {
        Pageable pageable = whitelist.apply(PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "availableQty")));

        assertThat(pageable.getSort().toList().getFirst().getProperty())
                .isEqualTo("balance.onHandQty - balance.reservedQty");
    }

    @Test
    void unknownFieldOrMultipleFieldsAreRejected() {
        assertThatThrownBy(() -> whitelist.apply(PageRequest.of(0, 20, Sort.by("passwordHash"))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("passwordHash")
                .hasMessageContaining("orderDate");

        assertThatThrownBy(() -> whitelist.apply(PageRequest.of(0, 20, Sort.by("orderDate", "itemCode"))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("一次只能依一個欄位排序");
    }
}
