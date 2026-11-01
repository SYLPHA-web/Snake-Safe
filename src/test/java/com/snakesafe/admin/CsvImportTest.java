package com.snakesafe.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import com.snakesafe.SnakeSafeTest;
import com.snakesafe.hospital.HospitalService;
import com.snakesafe.hospital.StockStatus;
import com.snakesafe.snake.SnakeRepository;
import com.snakesafe.web.ApiException;

/** Each test rolls back, so the shared sample data stays as other tests expect. */
@SnakeSafeTest
@Transactional
class CsvImportTest {

    @Autowired
    CsvImportService importer;
    @Autowired
    SnakeRepository snakes;
    @Autowired
    HospitalService hospitals;

    @Test
    void readsQuotedFieldsBomAndWindowsLineBreaks() {
        var rows = Csv.parse("﻿a,b\r\n\"x, y\",\"say \"\"hi\"\"\"\r\n\r\n1,2\r\n");
        assertThat(rows).hasSize(3);
        assertThat(rows.get(1)).containsExactly("x, y", "say \"hi\"");
    }

    @Test
    void importsATypicalPublicSnakeDatasetLayout() {
        String csv = "Family,Species,Common Name,Venomous,Distribution,Habitat\n"
                + "Viperidae,Trimeresurus gramineus,\"Bamboo pit viper, green\",Highly venomous,\"Karnataka, Kerala\",Bamboo and forest\n"
                + "Colubridae,Argyrogena ventromaculatus,Gliding racer,Non-venomous,Dry plains,Scrub\n"
                + "Colubridae,Naja naja,Indian cobra,Venomous,,\n"
                + "Colubridae,Mystery mysterii,Mystery snake,maybe,,\n";
        var r = importer.importSnakes(csv);
        assertThat(r.created()).isEqualTo(2);
        assertThat(r.updated()).isEqualTo(1);       // Naja naja already exists: updated, not duplicated
        assertThat(r.skipped()).isEqualTo(1);       // venom status unclear
        assertThat(r.errors()).singleElement().asString().contains("Mystery mysterii");

        var viper = snakes.findFirstByScientificNameIgnoreCase("trimeresurus gramineus").orElseThrow();
        assertThat(viper.venomous).isTrue();
        assertThat(viper.nameEn).isEqualTo("Bamboo pit viper, green");
        assertThat(viper.districts).isEqualTo("Karnataka, Kerala");
        assertThat(snakes.findFirstByScientificNameIgnoreCase("Argyrogena ventromaculatus").orElseThrow().venomous).isFalse();
        assertThat(snakes.findAll().stream().filter(s -> "Naja naja".equals(s.scientificName)).count()).isEqualTo(1);
    }

    @Test
    void venomWordsAreUnderstood() {
        assertThat(CsvImportService.parseVenom("Highly venomous")).isTrue();
        assertThat(CsvImportService.parseVenom("mildly venomous")).isTrue();
        assertThat(CsvImportService.parseVenom("Yes")).isTrue();
        assertThat(CsvImportService.parseVenom("Non-venomous")).isFalse();
        assertThat(CsvImportService.parseVenom("harmless")).isFalse();
        assertThat(CsvImportService.parseVenom("0")).isFalse();
        assertThat(CsvImportService.parseVenom("unknown")).isNull();
        assertThat(CsvImportService.parseVenom("")).isNull();
    }

    @Test
    void aFileWithoutAScientificNameColumnIsRefusedWithAClearCode() {
        assertThatThrownBy(() -> importer.importSnakes("colour,length\nred,2\n"))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo("CSV_COLUMNS"));
        assertThatThrownBy(() -> importer.importSnakes("species\n"))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo("CSV_EMPTY"));
    }

    @Test
    void importsHospitalsAndStampsTheirStockAsVerified() {
        String csv = "name,district,address,phone,latitude,longitude,vials\n"
                + "Test Taluk Hospital,Hassan,Main Road Hassan,08172-000111,13.0,76.1,9\n"
                + "Broken Row Hospital,Hassan,Somewhere,08172-000222,not-a-number,76.1,5\n";
        var r = importer.importHospitals(csv);
        assertThat(r.created()).isEqualTo(1);
        assertThat(r.skipped()).isEqualTo(1);
        var h = hospitals.list(null, null).stream().filter(x -> x.name().equals("Test Taluk Hospital")).findFirst().orElseThrow();
        assertThat(h.stock().vials()).isEqualTo(9);
        assertThat(h.stock().status()).isEqualTo(StockStatus.VERIFIED);
    }
}
