package com.paytend.l4pc.aml.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WatchlistIdentityRepository extends JpaRepository<WatchlistIdentityDO, String> {

    List<WatchlistIdentityDO> findByEntityId(String entityId);

    List<WatchlistIdentityDO> findByIdNumber(String idNumber);

    List<WatchlistIdentityDO> findByNationality(String nationality);

    void deleteByEntityId(String entityId);
}
