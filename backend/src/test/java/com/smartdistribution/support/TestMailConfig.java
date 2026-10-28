package com.smartdistribution.support;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;

/**
 * 所有整合測試都用 RecordingMailSender 取代真正的 SMTP，避免測試寄出真的信
 * 放在測試程式的套件底下，只有跑測試時會被掃描到
 */
@Configuration
public class TestMailConfig {

    @Bean
    @Primary
    public RecordingMailSender recordingMailSender() {
        return new RecordingMailSender();
    }

    /**
     * 正式環境的 @Async 會在背景執行緒寄信；測試改成在原本的執行緒直接執行，
     * 呼叫完 API 馬上就能檢查信件，不會因為信還沒寄完而時過時不過
     */
    @Bean
    public TaskExecutor taskExecutor() {
        return new SyncTaskExecutor();
    }
}
