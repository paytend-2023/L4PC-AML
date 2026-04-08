package com.paytend.l4pc.aml.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AlertRepository extends JpaRepository<AlertEntity, String> {

    List<AlertEntity> findByCustomerIdOrderByCreatedAtDesc(String customerId);

    List<AlertEntity> findByStatusOrderByCreatedAtDesc(String status);

    List<AlertEntity> findByScreeningId(String screeningId);
}
