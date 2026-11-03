package com.smartdistribution.shared.web.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * 只需要帶目前版本號的操作共用，例如送審、放棄草稿、撤回
 * 版本號和資料庫不同時回 409，避免用舊畫面的資料覆蓋別人的修改
 */
public record VersionRequest(
        @NotNull
        @PositiveOrZero
        Long version
) {
}
