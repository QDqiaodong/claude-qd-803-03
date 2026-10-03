package com.dyeing.mill.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.dyeing.mill.domain.ColorFamily;

public interface ColorFamilyRepository extends JpaRepository<ColorFamily, Long> {

    /**
     * 手写 JPQL：按深浅层级列出颜色族（维护页表格分组用）。
     */
    @Query("select f from ColorFamily f order by case f.depthLevel "
            + "when 'SPECIAL' then 0 when 'LIGHT' then 1 when 'DARK' then 2 else 3 end, f.familyCode")
    List<ColorFamily> findAllOrdered();

    Optional<ColorFamily> findByFamilyCode(String familyCode);
}
