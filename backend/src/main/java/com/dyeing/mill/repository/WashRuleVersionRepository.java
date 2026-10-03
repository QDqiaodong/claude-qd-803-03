package com.dyeing.mill.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dyeing.mill.domain.WashRuleVersion;

public interface WashRuleVersionRepository extends JpaRepository<WashRuleVersion, Long> {

    /**
     * 手写 JPQL：当前生效的清洗规则版本。
     */
    @Query("select v from WashRuleVersion v where v.status = 'ACTIVE'")
    Optional<WashRuleVersion> findActive();

    @Query("select v from WashRuleVersion v order by v.versionNo desc")
    List<WashRuleVersion> findAllOrdered();

    @Query("select v from WashRuleVersion v where v.status = 'ACTIVE' or v.id = :id")
    List<WashRuleVersion> findActiveOr(@Param("id") Long id);
}
