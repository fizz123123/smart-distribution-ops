package com.smartdistribution.receivable.service;

import java.time.LocalDate;

/**
 * 應收的到期狀態；會隨日期改變，所以不保存，每次依今天判斷
 * 清單篩選和單筆判斷都用這裡的日期範圍，規則只有一份
 */
public enum DueStatus {
    OVERDUE,  //到期日早於今天
    DUE_SOON; //今天到第 7 天之間到期，含頭尾

    private static final int DUE_SOON_DAYS = 7;

    /**
     * @return 範圍的第一天；逾期沒有下限，回 null
     */
    public LocalDate from(LocalDate today) {
        return this == OVERDUE ? null : today;
    }

    //範圍的最後一天，含當天
    public LocalDate to(LocalDate today) {
        return this == OVERDUE ? today.minusDays(1) : today.plusDays(DUE_SOON_DAYS);
    }

    /**
     * @return 到期日落在哪個範圍；都不是時回 null
     */
    public static DueStatus of(LocalDate dueDate, LocalDate today) {
        for (DueStatus dueStatus : values()) {
            LocalDate from = dueStatus.from(today);
            boolean afterFrom = from == null || !dueDate.isBefore(from);
            boolean beforeTo = !dueDate.isAfter(dueStatus.to(today));

            if (afterFrom && beforeTo) {
                return dueStatus;
            }
        }
        return null;
    }
}
