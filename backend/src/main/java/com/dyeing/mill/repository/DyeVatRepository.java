package com.dyeing.mill.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dyeing.mill.domain.DyeVat;

public interface DyeVatRepository extends JpaRepository<DyeVat, Long> {

    /**
     * 手写 JPQL：按机台类型统计缸数与该类型总容量。
     */
    @Query("select v.machineType, count(v), sum(v.capacityKg) from DyeVat v where v.deleted = 0 group by v.machineType")
    List<Object[]> statByMachineType();

    /**
     * 手写 JPQL：非空闲（在制）的染缸，按缸号排序。
     */
    @Query("select v from DyeVat v where v.status <> 'IDLE' order by v.vatCode")
    List<DyeVat> findRunningVats();

    @Query("select v from DyeVat v where v.workshop = :workshop order by v.vatCode")
    List<DyeVat> findByWorkshop(@Param("workshop") String workshop);
}
