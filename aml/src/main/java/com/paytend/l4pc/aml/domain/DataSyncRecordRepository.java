package com.paytend.l4pc.aml.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DataSyncRecordRepository extends JpaRepository<DataSyncRecordEntity, String> {

    List<DataSyncRecordEntity> findByProviderOrderByStartedAtDesc(String provider);
}
