package com.snakesafe.snake;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.snakesafe.SnakeSafeTest;

@SnakeSafeTest
class SnakeCatalogTest {

    private static final List<String> BANNED = List.of("tourniquet", "suck", "incision", "dosage", "antivenom dose", "triage",
            "protocol", "golden hour", "envenomation", "government");

    @Autowired
    SnakeRepository snakes;

    @Test
    void theReferenceListIsCompleteAndConsistent() {
        assertThat(SnakeCatalog.ALL).hasSizeGreaterThanOrEqualTo(30);
        var names = new HashSet<String>();
        for (var e : SnakeCatalog.ALL) {
            assertThat(names.add(e.scientificName().toLowerCase(Locale.ROOT))).as("duplicate %s", e.scientificName()).isTrue();
            assertThat(e.scientificName()).matches("[A-Z][a-z]+ [a-z]+");
            assertThat(e.nameKn()).as(e.nameEn()).matches(".*[\\u0C80-\\u0CFF].*");   // Kannada script present
            assertThat(e.description()).isNotBlank().hasSizeLessThan(1000);
            for (String bad : BANNED) {
                assertThat((e.description() + e.habitat()).toLowerCase(Locale.ROOT)).doesNotContain(bad);
            }
        }
    }

    @Test
    void theBigFourAreAllMarkedVenomous() {
        for (String s : List.of("Naja naja", "Bungarus caeruleus", "Daboia russelii", "Echis carinatus")) {
            assertThat(snakes.findFirstByScientificNameIgnoreCase(s).orElseThrow().venomous).as(s).isTrue();
        }
    }

    @Test
    void theReferenceListIsLoadedAtStartUp() {
        assertThat(snakes.count()).isGreaterThanOrEqualTo(SnakeCatalog.ALL.size());
    }
}
