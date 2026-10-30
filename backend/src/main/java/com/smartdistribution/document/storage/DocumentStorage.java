package com.smartdistribution.document.storage;

/**
 * 正式文件檔案的存放位置；開發環境存在本機資料夾，部署時換成 S3 的實作
 * 使用的地方只依賴這個介面，換實作時不用改呼叫端
 * storageKey 例如 purchase-orders/2026/PO-000001.pdf
 */
public interface DocumentStorage {

    void save(String storageKey, byte[] content);

    byte[] read(String storageKey);
}
