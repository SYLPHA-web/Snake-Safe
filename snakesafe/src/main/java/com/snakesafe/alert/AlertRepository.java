package com.snakesafe.alert;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AlertRepository extends JpaRepository<Alert, String> {

    List<Alert> findByStatusAndCancelWindowEndsAtLessThanEqual(AlertStatus status, Instant time);

    List<Alert> findByStatusAndSentAtLessThanEqual(AlertStatus status, Instant time);

    List<Alert> findByHospitalIdAndSentAtAfterOrderByCreatedAtDesc(Long hospitalId, Instant after);

    List<Alert> findTop200ByOrderByCreatedAtDesc();

    long countByHospitalIdAndStatus(Long hospitalId, AlertStatus status);
}
