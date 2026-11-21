package com.smartdistribution.shared.web.sort;

import com.smartdistribution.shared.exception.BusinessException;
import com.smartdistribution.shared.exception.ErrorCode;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.JpaSort;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 清單 API 的排序白名單，每個清單在 controller 宣告一份：
 * - API 只接受名單上的欄位，用回應裡的欄位名稱，例如 sort=orderDate,desc；一次只能一個
 * - 沒帶排序就用預設排序
 * - 最後一律補上 id，同值的資料每次查詢順序都一樣，翻頁才不會重複或漏掉
 *
 * <p>名單上的欄位對應到查詢裡的屬性路徑；可用量這類算出來的值，用運算式對應
 */
public final class SortWhitelist {

    private final Map<String, Sort> sorts;
    private final List<String> defaultFields;
    private final Sort.Direction defaultDirection;

    private SortWhitelist(Map<String, Sort> sorts, List<String> defaultFields, Sort.Direction defaultDirection) {
        this.sorts = sorts;
        this.defaultFields = defaultFields;
        this.defaultDirection = defaultDirection;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * 把前端送來的分頁參數換成實際查詢用的：檢查排序欄位、套用預設排序、補上 id
     *
     * @param pageable controller 收到的分頁參數
     * @return 頁碼與筆數不變，排序換成查詢用的屬性路徑
     */
    public Pageable apply(Pageable pageable) {
        List<Sort.Order> requested = pageable.getSort().toList();

        if (requested.size() > 1) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "一次只能依一個欄位排序。");
        }

        Sort sort;
        Sort.Direction direction;
        if (requested.isEmpty()) {
            direction = defaultDirection;
            sort = Sort.unsorted();
            for (String field : defaultFields) {
                sort = sort.and(sortOf(field, direction));
            }
        } else {
            Sort.Order order = requested.getFirst();
            direction = order.getDirection();
            sort = sortOf(order.getProperty(), direction);
        }

        return PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                sort.and(Sort.by(direction, "id"))
        );
    }

    private Sort sortOf(String field, Sort.Direction direction) {
        Sort sort = sorts.get(field);
        if (sort == null) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_FAILED,
                    "不支援依「" + field + "」排序，可用的欄位：" + String.join("、", sorts.keySet()) + "。"
            );
        }

        return direction == Sort.Direction.ASC ? sort.ascending() : sort.descending();
    }

    public static final class Builder {

        private final Map<String, Sort> sorts = new LinkedHashMap<>();
        private final List<String> defaultFields = new ArrayList<>();
        private Sort.Direction defaultDirection = Sort.Direction.ASC;

        private Builder() {
        }

        /**
         * 可以排序的欄位
         *
         * @param field 回應裡的欄位名稱，前端用它排序
         * @param path  查詢裡的屬性路徑，例如 customerCode、item.itemCode
         */
        public Builder field(String field, String path) {
            sorts.put(field, Sort.by(path));
            return this;
        }

        /**
         * 用運算式排序的欄位，例如可用量 ＝ 實體數量 － 已預留
         *
         * @param field      回應裡的欄位名稱
         * @param expression JPQL 運算式，只能由程式寫死，不能來自使用者輸入
         */
        public Builder expression(String field, String expression) {
            sorts.put(field, JpaSort.unsafe(expression));
            return this;
        }

        /**
         * 沒帶排序時的預設排序；多個欄位依序套用，方向相同
         *
         * @param direction 預設方向
         * @param fields    名單上的欄位名稱
         */
        public Builder defaultSort(Sort.Direction direction, String... fields) {
            this.defaultDirection = direction;
            this.defaultFields.addAll(List.of(fields));
            return this;
        }

        public SortWhitelist build() {
            if (defaultFields.isEmpty() || !sorts.keySet().containsAll(defaultFields)) {
                throw new IllegalStateException("預設排序必須是名單上的欄位");
            }

            return new SortWhitelist(Map.copyOf(sorts), List.copyOf(defaultFields), defaultDirection);
        }
    }
}
