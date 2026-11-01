package com.snakesafe.snake;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SnakeRepository extends JpaRepository<Snake, Long> {

    Optional<Snake> findFirstByScientificNameIgnoreCase(String scientificName);
}
