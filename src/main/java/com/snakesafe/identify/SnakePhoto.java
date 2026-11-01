package com.snakesafe.identify;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "snake_photos")
public class SnakePhoto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, length = 300)
    public String storagePath;

    @Column(nullable = false, length = 150)
    public String species;

    @Column(length = 100)
    public String verifiedBy;

    @Column(nullable = false, length = 50)
    public String source;

    public boolean consent;

    @Column(nullable = false)
    public Instant createdAt;
}
