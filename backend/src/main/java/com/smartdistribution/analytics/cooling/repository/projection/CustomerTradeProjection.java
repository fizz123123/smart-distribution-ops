package com.smartdistribution.analytics.cooling.repository.projection;

import java.time.LocalDate;

/**
 * 一個客戶到基準日為止的交易紀錄；交易日是已核准 SO 的訂單日期，同一天多張只算一個交易日
 *
 * @param active             客戶是否啟用；停用的客戶不評風險等級
 * @param approvedOrderCount 已核准 SO 張數
 * @param tradeDayCount      不重複的訂單日期個數
 */
public record CustomerTradeProjection(
        Long customerId,
        String customerCode,
        String customerName,
        boolean active,
        int approvedOrderCount,
        int tradeDayCount,
        LocalDate firstTradeDate,
        LocalDate lastTradeDate
) {
}
