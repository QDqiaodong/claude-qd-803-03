package com.dyeing.mill.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.dyeing.mill.domain.SchedulePlan;

public interface SchedulePlanRepository extends JpaRepository<SchedulePlan, Long> {

    /**
     * 手写 JPQL：方案列表按创建时间倒序（参考方案置顶）。
     */
    @Query("select p from SchedulePlan p order by case p.status when 'REFERENCE' then 0 else 1 end, p.createTime desc")
    List<SchedulePlan> findAllOrdered();

    @Query("select p from SchedulePlan p where p.status = 'REFERENCE'")
    Optional<SchedulePlan> findReference();
}
