package com.snakesafe.alert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.snakesafe.MutableClock;
import com.snakesafe.SnakeSafeTest;
import com.snakesafe.hospital.HospitalService;
import com.snakesafe.hospital.HospitalView;
import com.snakesafe.web.ApiException;

@SnakeSafeTest
class AlertStateMachineTest {

    @Autowired
    AlertService alerts;
    @Autowired
    HospitalService hospitals;
    @Autowired
    MutableClock clock;

    private Instant startedAt;
    private long bengaluru;

    @BeforeEach
    void setUp() {
        startedAt = clock.instant();
        bengaluru = hospitals.list(null, null).stream().filter(h -> h.name().endsWith("Bengaluru")).findFirst()
                .orElseThrow().id();
    }

    @AfterEach
    void restoreTime() {
        clock.setTo(startedAt);
    }

    private AlertView newAlert() {
        return alerts.create(new AlertRequest(PatientType.SELF, null, null, 12.97, 77.59, bengaluru), null);
    }

    private static void assertState(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getCode()).isEqualTo("INVALID_STATE"));
    }

    @Test
    void newAlertStartsInTheCancelWindowAndSaysSo() {
        AlertView a = newAlert();
        assertThat(a.status()).isEqualTo(AlertStatus.PENDING_CANCEL);
        assertThat(a.cancelSecondsLeft()).isEqualTo(5);
        assertThat(a.sentAt()).isNull();
    }

    @Test
    void nothingReachesTheHospitalDuringTheCancelWindow() {
        AlertView a = newAlert();
        clock.advance(Duration.ofSeconds(4));
        alerts.processTimeouts();
        assertThat(alerts.get(a.id()).status()).isEqualTo(AlertStatus.PENDING_CANCEL);
        assertThat(alerts.staffAlerts(bengaluru)).noneMatch(s -> s.id().equals(a.id()));
    }

    @Test
    void afterTheWindowItIsSentAndTheWordingIsTheHospitalWillConfirm() {
        AlertView a = newAlert();
        clock.advance(Duration.ofSeconds(6));
        alerts.processTimeouts();
        AlertView sent = alerts.get(a.id());
        assertThat(sent.status()).isEqualTo(AlertStatus.WAITING);
        assertThat(sent.message()).isEqualTo("The hospital will confirm");
        assertThat(alerts.staffAlerts(bengaluru)).anyMatch(s -> s.id().equals(a.id()));
    }

    @Test
    void cancelInsideTheWindowStopsItFromEverBeingSent() {
        AlertView a = newAlert();
        assertThat(alerts.cancel(a.id()).status()).isEqualTo(AlertStatus.CANCELLED);
        clock.advance(Duration.ofSeconds(30));
        alerts.processTimeouts();
        assertThat(alerts.get(a.id()).status()).isEqualTo(AlertStatus.CANCELLED);
        assertThat(alerts.staffAlerts(bengaluru)).noneMatch(s -> s.id().equals(a.id()));
        // cancelling again is harmless
        assertThat(alerts.cancel(a.id()).status()).isEqualTo(AlertStatus.CANCELLED);
    }

    @Test
    void cancelWhileWaitingIsAllowedAndTheHospitalSeesIt() {
        AlertView a = newAlert();
        clock.advance(Duration.ofSeconds(6));
        alerts.processTimeouts();
        assertThat(alerts.cancel(a.id()).status()).isEqualTo(AlertStatus.CANCELLED);
        assertThat(alerts.staffAlerts(bengaluru)).anySatisfy(s -> {
            assertThat(s.id()).isEqualTo(a.id());
            assertThat(s.status()).isEqualTo(AlertStatus.CANCELLED);
        });
        assertState(() -> alerts.staffAccept(bengaluru, a.id()));
    }

    @Test
    void noReplyForTwoMinutesMovesToNoReplyWithOtherHospitalsToTry() {
        AlertView a = newAlert();
        clock.advance(Duration.ofSeconds(6));
        alerts.processTimeouts();

        clock.advance(Duration.ofMinutes(1).plusSeconds(50));
        alerts.processTimeouts();
        assertThat(alerts.get(a.id()).status()).isEqualTo(AlertStatus.WAITING);

        clock.advance(Duration.ofSeconds(15));
        alerts.processTimeouts();
        AlertView late = alerts.get(a.id());
        assertThat(late.status()).isEqualTo(AlertStatus.NO_REPLY);
        assertThat(late.alternatives()).isNotEmpty().noneMatch(h -> h.id() == bengaluru);
        assertState(() -> alerts.staffAccept(bengaluru, a.id()));
    }

    @Test
    void acceptingGivesTheConfirmedWordingWithTheTime() {
        AlertView a = newAlert();
        clock.advance(Duration.ofSeconds(6));
        alerts.processTimeouts();
        StaffAlertView accepted = alerts.staffAccept(bengaluru, a.id());
        assertThat(accepted.statusLabel()).isEqualTo("Patient on the way");

        AlertView confirmed = alerts.get(a.id());
        assertThat(confirmed.status()).isEqualTo(AlertStatus.CONFIRMED);
        assertThat(confirmed.message()).matches("Antivenom available \\(confirmed \\d{2}:\\d{2}\\)");
        assertThat(confirmed.alternatives()).isNull();
        // a late timeout run must not undo the confirmation
        clock.advance(Duration.ofMinutes(10));
        alerts.processTimeouts();
        assertThat(alerts.get(a.id()).status()).isEqualTo(AlertStatus.CONFIRMED);
        // and it can no longer be cancelled
        assertState(() -> alerts.cancel(a.id()));
    }

    @Test
    void decliningGivesTheReasonAndOtherHospitals() {
        AlertView a = newAlert();
        clock.advance(Duration.ofSeconds(6));
        alerts.processTimeouts();
        alerts.staffDecline(bengaluru, a.id(), "No doctor free <b>now</b>");
        AlertView declined = alerts.get(a.id());
        assertThat(declined.status()).isEqualTo(AlertStatus.DECLINED);
        assertThat(declined.declineReason()).isEqualTo("No doctor free now");
        assertThat(declined.alternatives()).isNotEmpty();
    }

    @Test
    void staffOfAnotherHospitalCannotTouchTheAlert() {
        AlertView a = newAlert();
        clock.advance(Duration.ofSeconds(6));
        alerts.processTimeouts();
        HospitalView other = hospitals.list(null, null).stream().filter(h -> h.id() != bengaluru).findFirst().orElseThrow();
        assertThatThrownBy(() -> alerts.staffAccept(other.id(), a.id())).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getCode()).isEqualTo("ALERT_NOT_FOUND"));
    }

    @Test
    void staffCannotSeeOrActOnAnAlertStillInTheCancelWindow() {
        AlertView a = newAlert();
        assertThatThrownBy(() -> alerts.staffAccept(bengaluru, a.id())).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getCode()).isEqualTo("ALERT_NOT_FOUND"));
    }

    @Test
    void arrivedWorksFromConfirmedAndIsFinal() {
        AlertView a = newAlert();
        clock.advance(Duration.ofSeconds(6));
        alerts.processTimeouts();
        alerts.staffAccept(bengaluru, a.id());
        assertThat(alerts.arrived(a.id()).status()).isEqualTo(AlertStatus.ARRIVED);
        assertState(() -> alerts.cancel(a.id()));
    }

    @Test
    void cannotRaiseAnAlertForAHospitalWithNoStock() {
        long belagavi = hospitals.list(null, null).stream().filter(h -> h.name().endsWith("Belagavi")).findFirst()
                .orElseThrow().id();
        assertThatThrownBy(() -> alerts.create(new AlertRequest(PatientType.CHILD, "Asha", 7, 15.85, 74.5, belagavi), null))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo("NO_STOCK"));
    }
}
