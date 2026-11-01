package com.smartdistribution.document.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * PDF文件相關設定
 *
 * @param localDir 開發環境存放 PDF 的資料夾；相對路徑以應用程式啟動的位置為準
 */
@Validated
@ConfigurationProperties(prefix = "app.document")
public record DocumentProperties(
        @NotBlank String localDir
) {
}
