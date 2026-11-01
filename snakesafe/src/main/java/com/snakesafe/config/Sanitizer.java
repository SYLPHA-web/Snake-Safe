package com.snakesafe.config;

import java.util.regex.Pattern;

public final class Sanitizer {

    private static final Pattern TAGS = Pattern.compile("<[^>]*>");
    private static final Pattern CONTROL = Pattern.compile("[\\p{Cntrl}&&[^\\n\\t]]");

    private Sanitizer() {
    }

    public static String clean(String input) {
        if (input == null) {
            return null;
        }
        String text = TAGS.matcher(input).replaceAll("");
        text = CONTROL.matcher(text).replaceAll("");
        return text.replace("<", "").replace(">", "").strip();
    }

    public static String cleanOrNull(String input) {
        String text = clean(input);
        return text == null || text.isEmpty() ? null : text;
    }
}
