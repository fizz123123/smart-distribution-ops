package com.smartdistribution.shared.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * 業務上的「今天」由這個時鐘提供，時區只在這裡設定一次
 * 時間戳記（核准時間、JWT 到期等）仍用 Instant.now()：時間點和時區無關，也不需要在測試裡固定
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.system(ZoneId.of("Asia/Taipei"));
    }
}
