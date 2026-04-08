package com.paytend.l4pc.aml.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WatchlistEntityRepository extends JpaRepository<WatchlistEntityDO, String> {

    Optional<WatchlistEntityDO> findByProviderAndExternalId(String provider, String externalId);

    List<WatchlistEntityDO> findByStatus(String status);
}
