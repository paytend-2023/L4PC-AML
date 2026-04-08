package com.paytend.l4pc.aml.restriction;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface RiskCountryRepository extends JpaRepository<RiskCountryEntry, String> {

    @Query("SELECT r FROM RiskCountryEntry r WHERE r.countryCode = :countryCode AND r.status = 'ACTIVE'")
    List<RiskCountryEntry> findActiveByCountryCode(String countryCode);

    @Query("SELECT r FROM RiskCountryEntry r WHERE r.countryCode = :countryCode AND r.restrictionType = :type AND r.status = 'ACTIVE'")
    List<RiskCountryEntry> findActiveByCountryCodeAndType(String countryCode, RestrictionType type);

    @Query("SELECT CASE WHEN COUNT(r) > 0 THEN true ELSE false END FROM RiskCountryEntry r WHERE r.countryCode = :countryCode AND r.restrictionType = :type AND r.status = 'ACTIVE'")
    boolean existsActiveByCountryCodeAndType(String countryCode, RestrictionType type);

    List<RiskCountryEntry> findByStatusAndRestrictionType(String status, RestrictionType type);
}
