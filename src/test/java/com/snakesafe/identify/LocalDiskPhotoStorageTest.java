package com.snakesafe.identify;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;

import com.snakesafe.config.AppProperties;
import com.snakesafe.web.ApiException;

class LocalDiskPhotoStorageTest {

    @TempDir
    Path tempDir;

    private AppProperties createProps(String dir) {
        return new AppProperties(
                "SnakeSafe", "/img/logo.svg", "Fast help", "108", "1800-116-117",
                2, "en", "DEMO", "STUB", "STUB", 5, 2, false, true, "Asia/Kolkata",
                "LOCAL", dir, false, "secret123456789012345678901234567890", 24,
                null, null, new AppProperties.Limits(1000, 10, 10), false, true, "admin", "admin123", "staff123"
        );
    }

    @Test
    void savesFileSuccessfullyOnDisk() throws IOException {
        LocalDiskPhotoStorage storage = new LocalDiskPhotoStorage(createProps(tempDir.toString()));
        byte[] data = new byte[] { 1, 2, 3, 4, 5 };

        String returned = storage.save("photo-001.jpg", data, "image/jpeg");
        assertThat(returned).isEqualTo("photo-001.jpg");

        Path savedFile = tempDir.resolve("photo-001.jpg");
        assertThat(Files.exists(savedFile)).isTrue();
        assertThat(Files.readAllBytes(savedFile)).isEqualTo(data);
    }

    @Test
    void rejectsPathTraversalAttempts() {
        LocalDiskPhotoStorage storage = new LocalDiskPhotoStorage(createProps(tempDir.toString()));
        byte[] data = new byte[] { 9, 8, 7 };

        for (String maliciousName : new String[] { "../escape.jpg", "../../etc/passwd", "sub/../../outside.png" }) {
            assertThatThrownBy(() -> storage.save(maliciousName, data, "image/jpeg"))
                    .isInstanceOf(ApiException.class)
                    .satisfies(e -> {
                        ApiException ae = (ApiException) e;
                        assertThat(ae.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                        assertThat(ae.getCode()).isEqualTo("BAD_FILE_NAME");
                    });
        }
    }

    @Test
    void createsDirectoryTreeIfNeeded() {
        Path nested = tempDir.resolve("a/b/c/uploads");
        LocalDiskPhotoStorage storage = new LocalDiskPhotoStorage(createProps(nested.toString()));
        byte[] data = new byte[] { 42 };

        String saved = storage.save("nested.jpg", data, "image/jpeg");
        assertThat(saved).isEqualTo("nested.jpg");
        assertThat(Files.exists(nested.resolve("nested.jpg"))).isTrue();
    }
}
