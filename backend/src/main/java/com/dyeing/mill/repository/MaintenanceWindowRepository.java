package com.dyeing.mill.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dyeing.mill.domain.MaintenanceWindow;

public interface MaintenanceWindowRepository extends JpaRepository<MaintenanceWindow, Long> {

    /**
     * 手写 JPQL：与给定区间相交的某缸维护段（排程构造时间轴时用）。
     */
    @Query("select w from MaintenanceWindow w where w.vatId = :vatId "
            + "and w.startTime < :end and w.endTime > :start order by w.startTime")
    List<MaintenanceWindow> findOverlap(@Param("vatId") Long vatId,
                                        @Param("start") java.time.LocalDateTime start,
                                        @Param("end") java.time.LocalDateTime endTime);

    @Query("select w from MaintenanceWindow w order by w.startTime")
    List<MaintenanceWindow> findAllOrdered();
}
