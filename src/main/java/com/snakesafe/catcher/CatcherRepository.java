package com.snakesafe.catcher;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CatcherRepository extends JpaRepository<Catcher, Long> {

    List<Catcher> findByActiveTrue();

    Optional<Catcher> findByPhoneAndActiveTrue(String phone);

    Optional<Catcher> findFirstByPhoneAndActiveTrueOrderByIdAsc(String phone);

    boolean existsByPhoneAndActiveTrue(String phone);

    boolean existsByPhoneAndActiveTrueAndIdNot(String phone, Long id);

    long countByActiveTrue();
}
