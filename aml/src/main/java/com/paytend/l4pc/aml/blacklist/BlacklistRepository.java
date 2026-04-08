package com.paytend.l4pc.aml.blacklist;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface BlacklistRepository extends JpaRepository<BlacklistEntry, String> {

    @Query("SELECT b FROM BlacklistEntry b WHERE b.identifierType = :type AND b.identifierValue = :value AND b.status = 'ACTIVE'")
    List<BlacklistEntry> findActiveByTypeAndValue(BlacklistType type, String value);

    @Query("SELECT CASE WHEN COUNT(b) > 0 THEN true ELSE false END FROM BlacklistEntry b WHERE b.identifierType = :type AND b.identifierValue = :value AND b.status = 'ACTIVE'")
    boolean existsActiveByTypeAndValue(BlacklistType type, String value);

    List<BlacklistEntry> findByStatus(String status);
}
