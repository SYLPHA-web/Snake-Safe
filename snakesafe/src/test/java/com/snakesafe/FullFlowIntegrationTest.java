package com.snakesafe;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.snakesafe.alert.AlertService;

/**
 * The whole emergency over real HTTP and WebSockets:
 * best hospital -> alert -> cancel window -> waiting -> staff accepts -> patient arrives.
 */
@SnakeSafeTest
class FullFlowIntegrationTest {

    @LocalServerPort
    int port;
    @Autowired
    ObjectMapper json;
    @Autowired
    MutableClock clock;
    @Autowired
    AlertService alerts;

    private final HttpClient http = HttpClient.newHttpClient();
    private Instant startedAt;

    @BeforeEach
    void rememberTime() {
        startedAt = clock.instant();
    }

    @AfterEach
    void restoreTime() {
        clock.setTo(startedAt);
    }

    private JsonNode call(String method, String path, String body, String token, int expectedStatus) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json");
        if (token != null) {
            b.header("Authorization", "Bearer " + token);
        }
        b.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        HttpResponse<String> r = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
        assertThat(r.statusCode()).as("%s %s -> %s", method, path, r.body()).isEqualTo(expectedStatus);
        return r.body().isBlank() ? null : json.readTree(r.body());
    }

    private static class Listener implements WebSocket.Listener {
        final List<String> messages = new CopyOnWriteArrayList<>();
        private final StringBuilder partial = new StringBuilder();

        @Override
        public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
            partial.append(data);
            if (last) {
                messages.add(partial.toString());
                partial.setLength(0);
            }
            ws.request(1);
            return null;
        }

        boolean waitFor(String text) throws InterruptedException {
            for (int i = 0; i < 50; i++) {
                if (messages.stream().anyMatch(m -> m.contains(text))) {
                    return true;
                }
                TimeUnit.MILLISECONDS.sleep(100);
            }
            return false;
        }
    }

    @Test
    void emergencyFromFirstTapToArrival() throws Exception {
        // 1. guest asks for the best hospital: no login needed
        JsonNode best = call("GET", "/api/emergency/best-hospital?lat=12.98&lng=77.60", null, null, 200);
        assertThat(best.get("found").asBoolean()).isTrue();
        long hospitalId = best.get("hospital").get("id").asLong();
        assertThat(best.get("hospital").get("name").asText()).isEqualTo("Sample Hospital Bengaluru");
        assertThat(best.get("hospital").get("stock").get("status").asText()).isEqualTo("VERIFIED");

        // 2. hospital staff sign in and open their live feed
        JsonNode login = call("POST", "/api/staff/login",
                "{\"username\":\"staff-bengaluru\",\"password\":\"staff123\"}", null, 200);
        String staffToken = login.get("token").asText();
        Listener staffFeed = new Listener();
        WebSocket staffSocket = HttpClient.newHttpClient().newWebSocketBuilder()
                .buildAsync(URI.create("ws://localhost:" + port + "/ws/hospital/" + hospitalId + "?token=" + staffToken), staffFeed)
                .get(5, TimeUnit.SECONDS);
        assertThat(staffFeed.waitFor("snapshot")).isTrue();

        // 3. patient raises the alert as a guest: it starts inside the cancel window
        JsonNode created = call("POST", "/api/alerts",
                "{\"patientType\":\"CHILD\",\"patientName\":\"Asha\",\"patientAge\":7,\"lat\":12.98,\"lng\":77.60,\"hospitalId\":" + hospitalId + "}",
                null, 201);
        String id = created.get("id").asText();
        assertThat(created.get("status").asText()).isEqualTo("PENDING_CANCEL");
        Listener patientFeed = new Listener();
        WebSocket patientSocket = HttpClient.newHttpClient().newWebSocketBuilder()
                .buildAsync(URI.create("ws://localhost:" + port + "/ws/alerts/" + id), patientFeed)
                .get(5, TimeUnit.SECONDS);
        assertThat(patientFeed.waitFor("PENDING_CANCEL")).isTrue();

        // 4. during the window the hospital knows nothing
        assertThat(call("GET", "/api/staff/alerts", null, staffToken, 200).toString()).doesNotContain(id);
        assertThat(staffFeed.messages).noneMatch(m -> m.contains(id));

        // 5. window passes, the scheduler's job runs: alert goes to the hospital
        clock.advance(Duration.ofSeconds(6));
        alerts.processTimeouts();
        JsonNode waiting = call("GET", "/api/alerts/" + id, null, null, 200);
        assertThat(waiting.get("status").asText()).isEqualTo("WAITING");
        assertThat(waiting.get("message").asText()).isEqualTo("The hospital will confirm");
        assertThat(staffFeed.waitFor("alert_new")).isTrue();
        assertThat(patientFeed.waitFor("WAITING")).isTrue();
        JsonNode staffList = call("GET", "/api/staff/alerts", null, staffToken, 200);
        JsonNode mine = null;
        for (JsonNode a : staffList) {
            if (a.get("id").asText().equals(id)) {
                mine = a;
            }
        }
        assertThat(mine).as("the hospital now sees the alert").isNotNull();
        assertThat(mine.has("lat")).as("staff never get the patient's exact location").isFalse();

        // 6. staff accept: "Patient on the way" for them, "Antivenom available (confirmed HH:mm)" for the patient
        JsonNode accepted = call("POST", "/api/staff/alerts/" + id + "/accept", null, staffToken, 200);
        assertThat(accepted.get("statusLabel").asText()).isEqualTo("Patient on the way");
        JsonNode confirmed = call("GET", "/api/alerts/" + id, null, null, 200);
        assertThat(confirmed.get("status").asText()).isEqualTo("CONFIRMED");
        assertThat(confirmed.get("message").asText()).startsWith("Antivenom available (confirmed ");
        assertThat(patientFeed.waitFor("CONFIRMED")).isTrue();

        // 7. patient reaches the hospital
        JsonNode arrived = call("POST", "/api/alerts/" + id + "/arrived", null, null, 200);
        assertThat(arrived.get("status").asText()).isEqualTo("ARRIVED");
        assertThat(staffFeed.waitFor("ARRIVED")).isTrue();

        staffSocket.sendClose(WebSocket.NORMAL_CLOSURE, "done");
        patientSocket.sendClose(WebSocket.NORMAL_CLOSURE, "done");
    }

    @Test
    void protectedAreasNeedTheRightLoginAndErrorsHaveTheAgreedShape() throws Exception {
        assertThat(call("GET", "/api/profile", null, null, 401).get("error").asText()).isEqualTo("UNAUTHORIZED");
        assertThat(call("GET", "/api/staff/home", null, null, 401).get("error").asText()).isEqualTo("UNAUTHORIZED");
        assertThat(call("GET", "/api/admin/alerts", null, null, 401).get("error").asText()).isEqualTo("UNAUTHORIZED");

        String staffToken = call("POST", "/api/staff/login",
                "{\"username\":\"staff-mysuru\",\"password\":\"staff123\"}", null, 200).get("token").asText();
        assertThat(call("GET", "/api/admin/alerts", null, staffToken, 403).get("error").asText()).isEqualTo("FORBIDDEN");
        assertThat(call("GET", "/api/staff/home", null, staffToken, 200).get("hospital").get("name").asText())
                .isEqualTo("Sample Hospital Mysuru");

        call("POST", "/api/staff/login", "{\"username\":\"staff-mysuru\",\"password\":\"wrong\"}", null, 401);
        JsonNode bad = call("GET", "/api/emergency/best-hospital", null, null, 400);
        assertThat(bad.get("error").asText()).isEqualTo("LOCATION_OFF");
        assertThat(bad.get("message").asText()).isNotBlank();
        assertThat(call("POST", "/api/alerts", "{\"patientType\":\"SELF\"}", null, 400).get("error").asText())
                .isEqualTo("VALIDATION_ERROR");
        assertThat(call("GET", "/api/alerts/does-not-exist", null, null, 404).get("error").asText())
                .isEqualTo("ALERT_NOT_FOUND");
    }

    @Test
    void staffStockUpdateOverHttpMakesStockVerified() throws Exception {
        String token = call("POST", "/api/staff/login",
                "{\"username\":\"staff-shivamogga\",\"password\":\"staff123\"}", null, 200).get("token").asText();
        JsonNode stock = call("PUT", "/api/staff/stock", "{\"vials\":6}", token, 200);
        assertThat(stock.get("status").asText()).isEqualTo("VERIFIED");
        assertThat(stock.get("vials").asInt()).isEqualTo(6);
        call("PUT", "/api/staff/stock", "{\"vials\":-1}", token, 400);
    }

    @Test
    void firstAidIsSafeAndTheChatAlwaysSendsBitesToEmergencyHelp() throws Exception {
        String firstAid = call("GET", "/api/first-aid", null, null, 200).toString().toLowerCase();
        assertThat(firstAid).contains("108", "emergency", "ಹಾವು");
        for (String banned : List.of("tourniquet", "suck", "incision", "dose", "dosage", "mg ", "ibuprofen")) {
            assertThat(firstAid).doesNotContain(banned);
        }

        JsonNode chat = call("POST", "/api/chat", "{\"message\":\"My brother was bitten by a snake\",\"language\":\"en\"}",
                null, 200);
        assertThat(chat.get("reply").asText()).startsWith("Call 108 now");
        JsonNode kn = call("POST", "/api/chat", "{\"message\":\"ಹಾವು ಕಚ್ಚಿದೆ\",\"language\":\"kn\"}", null, 200);
        assertThat(kn.get("reply").asText()).startsWith("Call 108 now");
    }
}
