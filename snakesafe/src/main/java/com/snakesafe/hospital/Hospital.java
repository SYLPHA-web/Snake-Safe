package com.snakesafe.hospital;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "hospitals")
public class Hospital {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, length = 150)
    public String name;

    @Column(nullable = false, length = 100)
    public String district;

    @Column(nullable = false, length = 300)
    public String address;

    @Column(nullable = false, length = 20)
    public String phone;

    public double lat;

    public double lng;

    public boolean active = true;
}
