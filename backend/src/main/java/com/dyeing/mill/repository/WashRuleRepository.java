package com.dyeing.mill.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dyeing.mill.domain.WashRule;

public interface WashRuleRepository extends JpaRepository<WashRule, Long> {

    List<WashRule> findByVersionIdOrderByIdAsc(Long versionId);
}
