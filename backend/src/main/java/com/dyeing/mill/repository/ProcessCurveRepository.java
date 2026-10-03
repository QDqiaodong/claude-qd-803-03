package com.dyeing.mill.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dyeing.mill.domain.ProcessCurve;

public interface ProcessCurveRepository extends JpaRepository<ProcessCurve, Long> {

    /**
     * 手写 JPQL ⑤：按布种列出工艺曲线（前端曲线编辑器按布种切换）。
     */
    @Query("select c from ProcessCurve c where c.fabricType = :fabricType order by c.curveCode")
    List<ProcessCurve> findByFabricType(@Param("fabricType") String fabricType);

    @Query("select c from ProcessCurve c where c.colorCode = :colorCode order by c.id")
    List<ProcessCurve> findByColorCode(@Param("colorCode") String colorCode);
}
