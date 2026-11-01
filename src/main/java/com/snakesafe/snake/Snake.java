package com.snakesafe.snake;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "snakes")
public class Snake {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, length = 100)
    public String nameEn;

    @Column(nullable = false, length = 100)
    public String nameKn;

    @Column(nullable = false, length = 100)
    public String scientificName;

    public boolean venomous;

    @Column(length = 2000)
    public String description;

    @Column(length = 1000)
    public String habitat;

    /** Comma separated district names. "Statewide" means found in every district. */
    @Column(length = 1000)
    public String districts;

    @Column(length = 300)
    public String imageUrl;
}
