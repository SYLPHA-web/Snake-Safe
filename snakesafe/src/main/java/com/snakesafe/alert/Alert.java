package com.snakesafe.alert;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "alerts", indexes = { @Index(columnList = "status"), @Index(columnList = "hospitalId") })
public class Alert {

    /** Random UUID: whoever holds the id can follow the alert, so guests need no account. */
    @Id
    @Column(length = 36)
    public String id;

    public Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    public PatientType patientType;

    @Column(length = 100)
    public String patientName;

    public Integer patientAge;

    public double lat;

    public double lng;

    @Column(nullable = false)
    public Long hospitalId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    public AlertStatus status;

    @Column(nullable = false)
    public Instant createdAt;

    /** Until this moment the patient can cancel and nothing has reached the hospital. */
    @Column(nullable = false)
    public Instant cancelWindowEndsAt;

    public Instant sentAt;

    public Instant respondedAt;

    public Instant confirmedAt;

    public Instant cancelledAt;

    public Instant arrivedAt;

    @Column(length = 200)
    public String declineReason;

    @Version
    public Long version;
}
