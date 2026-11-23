package com.smartdistribution.analytics.common.service;

import com.smartdistribution.shared.exception.BusinessException;
import com.smartdistribution.shared.exception.ErrorCode;
import com.smartdistribution.shared.security.currentuser.CurrentUserProvider;
import com.smartdistribution.shared.security.userdetails.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 接單營收與交易降溫的查看範圍，依權限決定：
 * - DASHBOARD_VIEW_ALL 或 ANALYTICS_SALES_VIEW_ALL：全部，可以指定負責業務篩選
 * - 只有 ANALYTICS_SALES_VIEW：只看自己負責的 SO，不能指定別人
 */
@Component
@RequiredArgsConstructor
public class AnalyticsScope {

    private final CurrentUserProvider currentUserProvider;

    /**
     * @param requested 前端帶的負責業務；null 代表不篩選
     * @return 實際用來篩選的負責業務；null 代表全部
     */
    public Long resolveResponsibleSales(Long requested) {
        CustomUserDetails user = currentUserProvider.getCurrentUserDetails();
        if (user.hasAuthority("DASHBOARD_VIEW_ALL") || user.hasAuthority("ANALYTICS_SALES_VIEW_ALL")) {
            return requested;
        }

        if (requested != null && !requested.equals(user.getUserId())) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
        return user.getUserId();
    }
}
