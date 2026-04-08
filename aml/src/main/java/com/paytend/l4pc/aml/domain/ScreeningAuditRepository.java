package com.paytend.l4pc.aml.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ScreeningAuditRepository extends JpaRepository<ScreeningAuditEntity, String> {

    List<ScreeningAuditEntity> findByCustomerIdOrderByCreatedAtDesc(String customerId);

    List<ScreeningAuditEntity> findByStatusOrderByCreatedAtDesc(String status);
}
