package com.snakesafe.identify;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;

import com.snakesafe.web.ApiException;

/** Checks an upload really is a JPG or PNG by its first bytes, not by the name or type the client claims. */
final class ImageFiles {

    static final long MAX_BYTES = 5L * 1024 * 1024;

    record Checked(byte[] bytes, String mediaType, String extension) {
    }

    private ImageFiles() {
    }

    static Checked check(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("NO_FILE", "Please choose a photo.");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE", "The photo is too large. Maximum size is 5 MB.");
        }
        byte[] b;
        try {
            b = file.getBytes();
        } catch (IOException e) {
            throw ApiException.badRequest("BAD_FILE", "Could not read the photo.");
        }
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return new Checked(b, "image/jpeg", "jpg");
        }
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') {
            return new Checked(b, "image/png", "png");
        }
        throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_IMAGE", "Please upload a JPG or PNG photo.");
    }
}
