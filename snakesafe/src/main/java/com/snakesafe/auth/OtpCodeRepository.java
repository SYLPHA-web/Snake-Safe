package com.snakesafe.auth;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface OtpCodeRepository extends JpaRepository<OtpCode, Long> {

    Optional<OtpCode> findFirstByPhoneAndUsedFalseOrderByCreatedAtDesc(String phone);

    @Transactional
    @Modifying
    @Query("update OtpCode c set c.used = true where c.phone = :phone and c.used = false")
    void invalidateAll(String phone);

    @Transactional
    @Modifying
    @Query("delete from OtpCode c where c.expiresAt < :before")
    void deleteExpiredBefore(Instant before);
}
