package com.smartdistribution.inventory.service;

public interface InventoryService {

    /**
     * 公司料號建立時呼叫，在同一個交易中建立庫存餘額列（數量 0）。
     */
    void initializeForCompanyItem(Long companyItemId);
}
