package com.snakesafe.identify;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.http.HttpClient;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.snakesafe.config.AppProperties;
import com.snakesafe.web.ApiException;

class SupabaseStoragePhotoStorageTest {

    private AppProperties createProps(String url, String key, String bucket) {
        return new AppProperties(
                "SnakeSafe", "/img/logo.svg", "Fast help", "108", "1800-116-117",
                2, "en", "DEMO", "STUB", "STUB", 5, 2, false, true, "Asia/Kolkata",
                "SUPABASE", "uploads", false, "secret123456789012345678901234567890", 24,
                null, new AppProperties.Supabase(url, key, bucket),
                new AppProperties.Limits(1000, 10, 10), false, true, "admin", "admin123", "staff123"
        );
    }

    @Test
    void unconfiguredStorageThrowsServiceUnavailable() {
        AppProperties missingUrl = createProps("", "test-key", "snake-photos");
        SupabaseStoragePhotoStorage storageMissingUrl = new SupabaseStoragePhotoStorage(missingUrl);

        assertThatThrownBy(() -> storageMissingUrl.save("test.jpg", new byte[] { 1, 2, 3 }, "image/jpeg"))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> {
                    ApiException api = (ApiException) e;
                    assertThat(api.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(api.getCode()).isEqualTo("STORAGE_NOT_CONFIGURED");
                    assertThat(api.getMessage()).isEqualTo("Photo storage is not set up on this server.");
                });

        AppProperties missingKey = createProps("https://example.supabase.co", "", "snake-photos");
        SupabaseStoragePhotoStorage storageMissingKey = new SupabaseStoragePhotoStorage(missingKey);

        assertThatThrownBy(() -> storageMissingKey.save("test.jpg", new byte[] { 1, 2, 3 }, "image/jpeg"))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> {
                    ApiException api = (ApiException) e;
                    assertThat(api.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(api.getCode()).isEqualTo("STORAGE_NOT_CONFIGURED");
                });
    }

    @Test
    void extractsErrorFieldFromJsonResponse() {
        // When both 'error' and 'message' are present, 'error' is preferred
        String json = "{\"statusCode\":\"404\",\"error\":\"Bucket not found\",\"message\":\"The specified bucket was not found\"}";
        assertThat(SupabaseStoragePhotoStorage.extractErrorDetail(json)).isEqualTo("Bucket not found");
    }

    @Test
    void extractsMessageFieldWhenErrorFieldAbsent() {
        // When only 'message' is present
        String json = "{\"message\":\"Not Found\"}";
        assertThat(SupabaseStoragePhotoStorage.extractErrorDetail(json)).isEqualTo("Not Found");
    }

    @Test
    void fallbacksToFixedMessageWhenParsingFailsOrFieldsMissing() {
        // Null or blank input
        assertThat(SupabaseStoragePhotoStorage.extractErrorDetail(null))
                .isEqualTo(SupabaseStoragePhotoStorage.UNRECOGNIZED_ERROR);
        assertThat(SupabaseStoragePhotoStorage.extractErrorDetail("   "))
                .isEqualTo(SupabaseStoragePhotoStorage.UNRECOGNIZED_ERROR);

        // Non-JSON input (e.g., HTML from upstream proxy)
        assertThat(SupabaseStoragePhotoStorage.extractErrorDetail("<html><body>502 Bad Gateway</body></html>"))
                .isEqualTo(SupabaseStoragePhotoStorage.UNRECOGNIZED_ERROR);

        // JSON missing both error and message fields
        assertThat(SupabaseStoragePhotoStorage.extractErrorDetail("{\"statusCode\":500,\"success\":false}"))
                .isEqualTo(SupabaseStoragePhotoStorage.UNRECOGNIZED_ERROR);
    }

    @Test
    void sanitizesNewlinesAndLimitsLengthTo200() {
        // Newlines and carriage returns within the error field are stripped to single spaces
        String multiLine = "{\"error\":\"Bucket\\nnot\\r\\nfound\"}";
        assertThat(SupabaseStoragePhotoStorage.extractErrorDetail(multiLine)).isEqualTo("Bucket not found");

        // Long error strings are capped at 200 characters plus ellipsis
        String longError = "A".repeat(250);
        String json = "{\"error\":\"" + longError + "\"}";
        String extracted = SupabaseStoragePhotoStorage.extractErrorDetail(json);
        assertThat(extracted).startsWith("A".repeat(200));
        assertThat(extracted).endsWith("...");
        assertThat(extracted).hasSize(203);
    }

    @Test
    @SuppressWarnings("unchecked")
    void non2xxUploadThrowsStorageErrorWithOriginalMessage() throws Exception {
        HttpClient mockHttp = mock(HttpClient.class);
        HttpResponse<String> mockResponse = mock(HttpResponse.class);

        when(mockResponse.statusCode()).thenReturn(404);
        when(mockResponse.body()).thenReturn("{\n  \"statusCode\": \"404\",\n  \"error\": \"Bucket not found\"\n}");
        when(mockHttp.send(any(), any(HttpResponse.BodyHandler.class))).thenReturn(mockResponse);

        AppProperties props = createProps("https://example.supabase.co", "test-service-key", "snake-photos");
        SupabaseStoragePhotoStorage storage = new SupabaseStoragePhotoStorage(props, mockHttp);

        assertThatThrownBy(() -> storage.save("photo-123.jpg", new byte[] { 1, 2, 3 }, "image/jpeg"))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> {
                    ApiException api = (ApiException) e;
                    assertThat(api.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(api.getCode()).isEqualTo("STORAGE_ERROR");
                    assertThat(api.getMessage()).isEqualTo("Could not save the photo.");
                });
    }

    @Test
    @SuppressWarnings("unchecked")
    void successful2xxUploadReturnsFileName() throws Exception {
        HttpClient mockHttp = mock(HttpClient.class);
        HttpResponse<String> mockResponse = mock(HttpResponse.class);

        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn("{\"Key\":\"snake-photos/photo-456.jpg\"}");
        when(mockHttp.send(any(), any(HttpResponse.BodyHandler.class))).thenReturn(mockResponse);

        AppProperties props = createProps("https://example.supabase.co/", "test-service-key", "snake-photos");
        SupabaseStoragePhotoStorage storage = new SupabaseStoragePhotoStorage(props, mockHttp);

        String result = storage.save("photo-456.jpg", new byte[] { 1, 2, 3 }, "image/jpeg");
        assertThat(result).isEqualTo("photo-456.jpg");
    }
}
