package com.snakesafe.profile;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Table(name = "family_members", indexes = @Index(columnList = "userId"))
public class FamilyMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false)
    public Long userId;

    @Column(nullable = false, length = 100)
    public String name;

    public Integer age;

    @Column(length = 50)
    public String relation;

    @Column(length = 500)
    public String allergies;

    @Column(length = 500)
    public String notes;
}
