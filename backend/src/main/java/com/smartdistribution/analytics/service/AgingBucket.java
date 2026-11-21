package com.smartdistribution.analytics.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * 應收帳齡：未收餘額依逾期天數分組，還沒到期的另成一組
 */
public enum AgingBucket {
    NOT_DUE,    //還沒到期，含今天到期
    DAYS_1_30,  //逾期 1～30 天
    DAYS_31_60,
    DAYS_61_90,
    OVER_90;    //逾期 91 天以上

    /**
     * @param dueDate 到期日
     * @param today   今天（台北時間）
     * @return 這筆應收屬於哪一組
     */
    public static AgingBucket of(LocalDate dueDate, LocalDate today) {
        long daysPastDue = ChronoUnit.DAYS.between(dueDate, today);

        if (daysPastDue <= 0) {
            return NOT_DUE;
        }
        if (daysPastDue <= 30) {
            return DAYS_1_30;
        }
        if (daysPastDue <= 60) {
            return DAYS_31_60;
        }
        if (daysPastDue <= 90) {
            return DAYS_61_90;
        }
        return OVER_90;
    }
}
