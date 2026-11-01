package com.snakesafe.catcher;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CatcherRepository extends JpaRepository<Catcher, Long> {

    List<Catcher> findByActiveTrue();
}
