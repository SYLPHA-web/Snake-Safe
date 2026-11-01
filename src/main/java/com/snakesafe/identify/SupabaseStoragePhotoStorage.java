package com.snakesafe.identify;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.snakesafe.config.AppProperties;
import com.snakesafe.web.ApiException;

/** Uploads to a Supabase Storage bucket (default "snake-photos") with the server-side service key. */
@Component
@ConditionalOnProperty(name = "app.photo-storage", havingValue = "SUPABASE")
public class SupabaseStoragePhotoStorage implements PhotoStorage {

    private static final Logger log = LoggerFactory.getLogger(SupabaseStoragePhotoStorage.class);

    private final AppProperties.Supabase config;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public SupabaseStoragePhotoStorage(AppProperties props) {
        this.config = props.supabase();
    }

    @Override
    public String save(String fileName, byte[] data, String contentType) {
        if (config.url().isBlank() || config.serviceKey().isBlank()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "STORAGE_NOT_CONFIGURED",
                    "Photo storage is not set up on this server.");
        }
        String base = config.url().endsWith("/") ? config.url().substring(0, config.url().length() - 1) : config.url();
        HttpRequest request = HttpRequest.newBuilder(URI.create(base + "/storage/v1/object/" + config.bucket() + "/" + fileName))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + config.serviceKey())
                .header("apikey", config.serviceKey())
                .header("Content-Type", contentType)
                .POST(HttpRequest.BodyPublishers.ofByteArray(data))
                .build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                log.warn("Supabase storage upload failed with status {}", response.statusCode());
                throw new ApiException(HttpStatus.BAD_GATEWAY, "STORAGE_ERROR", "Could not save the photo.");
            }
        } catch (IOException e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "STORAGE_ERROR", "Could not save the photo.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(HttpStatus.BAD_GATEWAY, "STORAGE_ERROR", "Could not save the photo.");
        }
        return fileName;
    }
}
