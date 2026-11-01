package com.snakesafe.catcher;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "rescue_requests")
public class Rescue {

    @Id
    @Column(length = 36)
    public String id;

    @Column(nullable = false)
    public Long catcherId;

    public double lat;

    public double lng;

    @Column(length = 500)
    public String notes;

    @Column(nullable = false, length = 20)
    public String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    public RescueStatus status;

    @Column(nullable = false)
    public Instant createdAt;

    @Column(nullable = false)
    public Instant updatedAt;
}
