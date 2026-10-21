package com.smartdistribution.masterdata.common;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 系統可使用的單位，單位只能透過修改此列舉並重新部署新增，
 * 各料號的換算倍率另存在 item_unit_conversion，不寫在這裡
 */
@Getter
@RequiredArgsConstructor
public enum UnitOfMeasure {

    PCS("個", 0),
    STICK("支", 0),
    SET("組", 0),
    BOX("箱", 0),
    PKG("包", 0),
    ROL("捲", 0),
    SHT("片", 0),
    CAN("罐", 0),
    M("米", 2),
    M2("平方公尺", 2),
    KG("公斤", 3),
    TON("噸", 3),
    L("公升", 3);

    private final String displayName;

    //單位的小數點位數，於 SO／PO 輸入數量時檢查
    private final int decimalPlaces;
}
