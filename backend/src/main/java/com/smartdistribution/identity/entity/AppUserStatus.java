package com.smartdistribution.identity.entity;

public enum AppUserStatus {
    PENDING, //管理員已建立帳號，使用者還沒用信中的連結設定密碼，不能登入
    ACTIVE,
    DISABLED
}
