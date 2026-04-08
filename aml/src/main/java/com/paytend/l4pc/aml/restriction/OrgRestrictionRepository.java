package com.paytend.l4pc.aml.restriction;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface OrgRestrictionRepository extends JpaRepository<OrgRestrictionEntry, String> {

    @Query("SELECT o FROM OrgRestrictionEntry o WHERE o.countryCode = :countryCode AND o.restrictionField = :field AND o.status = 'ACTIVE'")
    List<OrgRestrictionEntry> findActiveByCountryCodeAndField(String countryCode, String field);

    @Query("SELECT CASE WHEN COUNT(o) > 0 THEN true ELSE false END FROM OrgRestrictionEntry o WHERE o.countryCode = :countryCode AND o.restrictionField = :field AND o.status = 'ACTIVE'")
    boolean existsActiveByCountryCodeAndField(String countryCode, String field);
}
