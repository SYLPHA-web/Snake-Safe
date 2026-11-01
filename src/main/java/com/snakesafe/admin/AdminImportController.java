package com.snakesafe.admin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.snakesafe.web.ApiException;

/** Admin: bulk-load snakes or hospitals from a CSV file (multipart field "file"). */
@RestController
@RequestMapping("/api/admin/import")
public class AdminImportController {

    private static final long MAX_BYTES = 2L * 1024 * 1024;

    private final CsvImportService importer;

    public AdminImportController(CsvImportService importer) {
        this.importer = importer;
    }

    @PostMapping(value = "/{type}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CsvImportService.Result importCsv(@PathVariable String type, @RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("NO_FILE", "Please choose a CSV file.");
        }
        if (file.getSize() > MAX_BYTES) {
            throw ApiException.badRequest("CSV_TOO_BIG", "The CSV file is too large (maximum 2 MB).");
        }
        String text;
        try {
            text = new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw ApiException.badRequest("BAD_FILE", "Could not read the file.");
        }
        return switch (type) {
            case "snakes" -> importer.importSnakes(text);
            case "hospitals" -> importer.importHospitals(text);
            default -> throw ApiException.notFound("NOT_FOUND", "Unknown import type.");
        };
    }
}
