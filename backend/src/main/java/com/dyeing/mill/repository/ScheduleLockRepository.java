package com.dyeing.mill.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dyeing.mill.domain.ScheduleLock;

public interface ScheduleLockRepository extends JpaRepository<ScheduleLock, Long> {

    /**
     * 手写 JPQL：与给定区间相交的某缸锁定段（含仅封缸段，重算不得跨越）。
     */
    @Query("select l from ScheduleLock l where l.vatId = :vatId "
            + "and l.startTime < :end and l.endTime > :start order by l.startTime")
    List<ScheduleLock> findOverlap(@Param("vatId") Long vatId,
                                   @Param("start") java.time.LocalDateTime start,
                                   @Param("end") java.time.LocalDateTime endTime);

    @Query("select l from ScheduleLock l order by l.startTime")
    List<ScheduleLock> findAllOrdered();
}
