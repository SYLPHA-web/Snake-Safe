package com.snakesafe.profile;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FamilyMemberRepository extends JpaRepository<FamilyMember, Long> {

    List<FamilyMember> findByUserIdOrderByIdAsc(Long userId);

    Optional<FamilyMember> findByIdAndUserId(Long id, Long userId);

    long countByUserId(Long userId);
}
