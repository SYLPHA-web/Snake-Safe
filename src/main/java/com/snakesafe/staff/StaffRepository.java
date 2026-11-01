package com.snakesafe.staff;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StaffRepository extends JpaRepository<Staff, Long> {

    Optional<Staff> findByUsernameIgnoreCase(String username);

    List<Staff> findAllByOrderByUsernameAsc();
}
