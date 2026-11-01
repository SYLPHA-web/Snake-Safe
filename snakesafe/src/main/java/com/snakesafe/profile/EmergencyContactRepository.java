package com.snakesafe.profile;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EmergencyContactRepository extends JpaRepository<EmergencyContact, Long> {

    List<EmergencyContact> findByUserIdOrderByIdAsc(Long userId);

    Optional<EmergencyContact> findByIdAndUserId(Long id, Long userId);

    long countByUserId(Long userId);
}
