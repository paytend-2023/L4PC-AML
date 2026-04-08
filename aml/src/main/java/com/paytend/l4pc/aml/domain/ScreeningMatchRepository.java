package com.paytend.l4pc.aml.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ScreeningMatchRepository extends JpaRepository<ScreeningMatchEntity, String> {

    List<ScreeningMatchEntity> findByScreeningIdOrderByMatchScoreDesc(String screeningId);
}
