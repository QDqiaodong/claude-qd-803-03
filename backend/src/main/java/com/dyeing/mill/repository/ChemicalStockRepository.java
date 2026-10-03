package com.dyeing.mill.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dyeing.mill.domain.ChemicalStock;
import com.dyeing.mill.dto.StockAlertDTO;

public interface ChemicalStockRepository extends JpaRepository<ChemicalStock, Long> {

    /**
     * 手写 JPQL ④：染料库存预警 —— 库存低于安全库存的染化料，缺口越大排越前。
     */
    @Query("select new com.dyeing.mill.dto.StockAlertDTO(s.id, s.chemCode, s.chemName, s.category, "
            + "s.stockKg, s.safetyKg, s.expireDate) "
            + "from ChemicalStock s where s.deleted = 0 and s.safetyKg > 0 and s.stockKg <= s.safetyKg "
            + "order by (s.stockKg / s.safetyKg) asc")
    List<StockAlertDTO> findStockAlerts();

    @Query("select s from ChemicalStock s where s.category = :category order by s.chemCode")
    List<ChemicalStock> findByCategory(@Param("category") String category);

    @Query("select s from ChemicalStock s where s.expireDate < current_date order by s.expireDate")
    List<ChemicalStock> findExpired();
}
