package com.smartdistribution.document.storage;

import com.smartdistribution.document.config.DocumentProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 把正式文件存在本機資料夾，開發與測試使用
 */
@Component
public class LocalDocumentStorage implements DocumentStorage {

    private final Path rootDir;

    public LocalDocumentStorage(DocumentProperties documentProperties) {
        this.rootDir = Path.of(documentProperties.localDir()).toAbsolutePath().normalize();
    }

    @Override
    public void save(String storageKey, byte[] content) {
        Path target = resolve(storageKey);

        try {
            Files.createDirectories(target.getParent());
            Files.write(target, content);
        } catch (IOException exception) {
            throw new UncheckedIOException("無法寫入正式文件：" + storageKey, exception);
        }
    }

    @Override
    public byte[] read(String storageKey) {
        try {
            return Files.readAllBytes(resolve(storageKey));
        } catch (IOException exception) {
            throw new UncheckedIOException("無法讀取正式文件：" + storageKey, exception);
        }
    }

    //storageKey 由系統產生，這裡再確認一次不會跑出存放資料夾之外
    private Path resolve(String storageKey) {
        Path target = rootDir.resolve(storageKey).normalize();

        if (!target.startsWith(rootDir)) {
            throw new IllegalArgumentException("不合法的文件位置：" + storageKey);
        }

        return target;
    }
}
