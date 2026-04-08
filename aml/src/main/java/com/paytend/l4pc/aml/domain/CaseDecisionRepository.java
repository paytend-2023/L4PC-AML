package com.paytend.l4pc.aml.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CaseDecisionRepository extends JpaRepository<CaseDecisionEntity, String> {

    List<CaseDecisionEntity> findByAlertIdOrderByCreatedAtDesc(String alertId);
}
