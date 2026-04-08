package com.paytend.l4pc.aml.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ScreeningCustomerRepository extends JpaRepository<ScreeningCustomerEntity, String> {

    Optional<ScreeningCustomerEntity> findByCustomerId(String customerId);

    @Query("SELECT c FROM ScreeningCustomerEntity c WHERE c.status = 'ACTIVE' AND c.nextScreenedAt <= :now")
    List<ScreeningCustomerEntity> findDueForScreening(@Param("now") Instant now);

    List<ScreeningCustomerEntity> findByRiskLevelAndStatus(String riskLevel, String status);
}
