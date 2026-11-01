package com.snakesafe.auth;

import com.snakesafe.web.ApiException;

public final class Phones {

    private Phones() {
    }

    /** Returns +91XXXXXXXXXX style numbers; throws INVALID_PHONE for anything that cannot be a phone number. */
    public static String normalize(String raw) {
        if (raw == null) {
            throw ApiException.badRequest("INVALID_PHONE", "Enter a valid phone number.");
        }
        String p = raw.replaceAll("[\\s\\-()]", "");
        if (p.matches("[6-9]\\d{9}")) {
            p = "+91" + p;
        } else if (p.matches("91[6-9]\\d{9}")) {
            p = "+" + p;
        } else if (p.matches("0[6-9]\\d{9}")) {
            p = "+91" + p.substring(1);
        }
        if (!p.matches("\\+\\d{10,15}")) {
            throw ApiException.badRequest("INVALID_PHONE", "Enter a valid phone number.");
        }
        return p;
    }
}
