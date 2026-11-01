package com.snakesafe.admin;

import java.util.ArrayList;
import java.util.List;

/** Small CSV reader: quoted fields, doubled quotes, commas and line breaks inside quotes, BOM, CRLF. */
final class Csv {

    private Csv() {
    }

    static List<List<String>> parse(String text) {
        List<List<String>> rows = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return rows;
        }
        if (text.charAt(0) == '﻿') {
            text = text.substring(1);
        }
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                        cell.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    cell.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == ',') {
                row.add(cell.toString());
                cell.setLength(0);
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                    i++;
                }
                row.add(cell.toString());
                cell.setLength(0);
                addIfNotBlank(rows, row);
                row = new ArrayList<>();
            } else {
                cell.append(c);
            }
        }
        row.add(cell.toString());
        addIfNotBlank(rows, row);
        return rows;
    }

    private static void addIfNotBlank(List<List<String>> rows, List<String> row) {
        if (row.stream().anyMatch(v -> !v.isBlank())) {
            rows.add(row);
        }
    }
}
