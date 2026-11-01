package com.snakesafe.catcher;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RescueRepository extends JpaRepository<Rescue, String> {

    List<Rescue> findTop200ByOrderByCreatedAtDesc();
}
