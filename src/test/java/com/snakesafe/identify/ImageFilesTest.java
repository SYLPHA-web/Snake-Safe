package com.snakesafe.identify;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

import com.snakesafe.web.ApiException;

class ImageFilesTest {

    private static final byte[] JPEG_HEADER = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0 };
    private static final byte[] PNG_HEADER = { (byte) 0x89, (byte) 'P', (byte) 'N', (byte) 'G', 0x0D, 0x0A, 0x1A, 0x0A };

    @Test
    void acceptsValidJpegMagicBytes() {
        MockMultipartFile file = new MockMultipartFile("file", "snake.jpg", "image/jpeg", JPEG_HEADER);
        ImageFiles.Checked checked = ImageFiles.check(file);

        assertThat(checked).isNotNull();
        assertThat(checked.mediaType()).isEqualTo("image/jpeg");
        assertThat(checked.extension()).isEqualTo("jpg");
        assertThat(checked.bytes()).isEqualTo(JPEG_HEADER);
    }

    @Test
    void acceptsValidPngMagicBytes() {
        MockMultipartFile file = new MockMultipartFile("file", "snake.png", "image/png", PNG_HEADER);
        ImageFiles.Checked checked = ImageFiles.check(file);

        assertThat(checked).isNotNull();
        assertThat(checked.mediaType()).isEqualTo("image/png");
        assertThat(checked.extension()).isEqualTo("png");
        assertThat(checked.bytes()).isEqualTo(PNG_HEADER);
    }

    @Test
    void rejectsNullOrEmptyFile() {
        assertThatThrownBy(() -> ImageFiles.check(null))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> {
                    ApiException ae = (ApiException) e;
                    assertThat(ae.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ae.getCode()).isEqualTo("NO_FILE");
                });

        MockMultipartFile empty = new MockMultipartFile("file", "empty.jpg", "image/jpeg", new byte[0]);
        assertThatThrownBy(() -> ImageFiles.check(empty))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> {
                    ApiException ae = (ApiException) e;
                    assertThat(ae.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ae.getCode()).isEqualTo("NO_FILE");
                });
    }

    @Test
    void enforcesFiveMegabyteBoundary() {
        // Exactly 5 MB (5 * 1024 * 1024 bytes)
        int exactLimit = (int) ImageFiles.MAX_BYTES;
        byte[] valid5Mb = new byte[exactLimit];
        System.arraycopy(JPEG_HEADER, 0, valid5Mb, 0, JPEG_HEADER.length);

        MockMultipartFile atLimit = new MockMultipartFile("file", "exact.jpg", "image/jpeg", valid5Mb);
        ImageFiles.Checked checked = ImageFiles.check(atLimit);
        assertThat(checked).isNotNull();
        assertThat(checked.bytes()).hasSize(exactLimit);

        // 5 MB + 1 byte
        byte[] oversized = new byte[exactLimit + 1];
        System.arraycopy(JPEG_HEADER, 0, oversized, 0, JPEG_HEADER.length);
        MockMultipartFile overLimit = new MockMultipartFile("file", "oversized.jpg", "image/jpeg", oversized);

        assertThatThrownBy(() -> ImageFiles.check(overLimit))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> {
                    ApiException ae = (ApiException) e;
                    assertThat(ae.getStatus()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
                    assertThat(ae.getCode()).isEqualTo("FILE_TOO_LARGE");
                });
    }

    @Test
    void rejectsUnsupportedOrCorruptFiles() {
        // Text file claiming to be JPEG
        byte[] textBytes = "This is not an image".getBytes();
        MockMultipartFile fakeJpeg = new MockMultipartFile("file", "fake.jpg", "image/jpeg", textBytes);
        assertThatThrownBy(() -> ImageFiles.check(fakeJpeg))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> {
                    ApiException ae = (ApiException) e;
                    assertThat(ae.getStatus()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
                    assertThat(ae.getCode()).isEqualTo("UNSUPPORTED_IMAGE");
                });

        // GIF image header
        byte[] gifBytes = { 'G', 'I', 'F', '8', '9', 'a', 0x01, 0x00 };
        MockMultipartFile gif = new MockMultipartFile("file", "anim.gif", "image/gif", gifBytes);
        assertThatThrownBy(() -> ImageFiles.check(gif))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> {
                    ApiException ae = (ApiException) e;
                    assertThat(ae.getStatus()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
                    assertThat(ae.getCode()).isEqualTo("UNSUPPORTED_IMAGE");
                });

        // Truncated bytes (< 3 bytes)
        byte[] truncated = { (byte) 0xFF, (byte) 0xD8 };
        MockMultipartFile shortFile = new MockMultipartFile("file", "short.jpg", "image/jpeg", truncated);
        assertThatThrownBy(() -> ImageFiles.check(shortFile))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> {
                    ApiException ae = (ApiException) e;
                    assertThat(ae.getStatus()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
                    assertThat(ae.getCode()).isEqualTo("UNSUPPORTED_IMAGE");
                });
    }
}
