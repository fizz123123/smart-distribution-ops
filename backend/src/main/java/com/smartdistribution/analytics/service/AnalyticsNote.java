package com.smartdistribution.analytics.service;

/**
 * 指標算不出來時附上的說明，前端依代碼顯示對應的文字，不顯示誤導的數字
 */
public enum AnalyticsNote {
    NO_PRIOR_BASELINE,   //前期是 0，不算變化率
    INSUFFICIENT_DATA,   //資料不足，例如沒有出貨、交易日不到 3 天
    NO_RECENT_USAGE,     //近 30 天沒有出貨，不算可支撐天數
    INSUFFICIENT_HISTORY //沒有完成的採購紀錄，不算採購週期
}
