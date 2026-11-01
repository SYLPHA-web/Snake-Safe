package com.snakesafe.staff;

import com.snakesafe.auth.Role;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Hospital staff (role STAFF, tied to one hospital) and administrators (role ADMIN, no hospital). */
@Entity
@Table(name = "staff")
public class Staff {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, unique = true, length = 60)
    public String username;

    @Column(nullable = false, length = 100)
    public String passwordHash;

    @Column(nullable = false, length = 100)
    public String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    public Role role;

    public Long hospitalId;

    public boolean active = true;
}
