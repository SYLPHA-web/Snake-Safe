package com.snakesafe.identify;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.snakesafe.config.AppProperties;
import com.snakesafe.web.ApiException;

@Component
@ConditionalOnProperty(name = "app.photo-storage", havingValue = "LOCAL", matchIfMissing = true)
public class LocalDiskPhotoStorage implements PhotoStorage {

    private final Path root;

    public LocalDiskPhotoStorage(AppProperties props) {
        this.root = Path.of(props.photoDir()).toAbsolutePath().normalize();
    }

    @Override
    public String save(String fileName, byte[] data, String contentType) {
        Path target = root.resolve(fileName).normalize();
        if (!target.startsWith(root)) {
            throw ApiException.badRequest("BAD_FILE_NAME", "Invalid file name.");
        }
        try {
            Files.createDirectories(root);
            Files.write(target, data);
        } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "STORAGE_ERROR", "Could not save the photo.");
        }
        return fileName;
    }
}
