package com.paytend.l4pc.aml.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface WatchlistNameRepository extends JpaRepository<WatchlistNameDO, String> {

    List<WatchlistNameDO> findByEntityId(String entityId);

    @Query("SELECT n FROM WatchlistNameDO n WHERE n.soundexCode = :soundex")
    List<WatchlistNameDO> findBySoundexCode(@Param("soundex") String soundexCode);

    @Query("SELECT n FROM WatchlistNameDO n WHERE LOWER(n.normalizedName) LIKE LOWER(CONCAT('%', :name, '%'))")
    List<WatchlistNameDO> findByNormalizedNameContaining(@Param("name") String name);

    void deleteByEntityId(String entityId);
}
