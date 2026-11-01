package com.snakesafe.hospital;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StockRepository extends JpaRepository<AntivenomStock, Long> {

    Optional<AntivenomStock> findByHospitalId(Long hospitalId);

    List<AntivenomStock> findByHospitalIdIn(Collection<Long> hospitalIds);
}
