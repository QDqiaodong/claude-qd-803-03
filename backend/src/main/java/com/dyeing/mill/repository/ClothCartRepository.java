package com.dyeing.mill.repository;

import java.time.LocalDateTime;
import java.util.List;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import com.dyeing.mill.domain.ClothCart;
import com.dyeing.mill.dto.ColorStatDTO;
import com.dyeing.mill.dto.CustomerMeterDTO;

public interface ClothCartRepository extends JpaRepository<ClothCart, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("select c from ClothCart c where c.id = :id")
    java.util.Optional<ClothCart> findByIdForUpdate(@Param("id") Long id);

    /**
     * 手写 JPQL ①：按色号统计缸次（缸次数 + 总米数）。
     */
    @Query("select new com.dyeing.mill.dto.ColorStatDTO(c.colorCode, max(c.colorName), count(c), sum(c.meters)) "
            + "from ClothCart c where c.deleted = 0 group by c.colorCode order by count(c) desc")
    List<ColorStatDTO> statByColorCode();

    /**
     * 手写 JPQL ②：按客户汇总米数（布车联配方取客户）。
     */
    @Query("select new com.dyeing.mill.dto.CustomerMeterDTO(r.customer, count(c), sum(c.meters)) "
            + "from ClothCart c, Recipe r "
            + "where r.colorCode = c.colorCode and r.deleted = 0 and c.deleted = 0 "
            + "group by r.customer order by sum(c.meters) desc")
    List<CustomerMeterDTO> statMetersByCustomer();

    /**
     * 手写 JPQL ③：同一染缸上时段的在制布车（排缸时间冲突校验用）。
     */
    @Query("select c from ClothCart c where c.vatId = :vatId and c.id <> :cartId "
            + "and c.dyeStartTime is not null and c.dyeEndTime is not null "
            + "and c.dyeStartTime < :end and c.dyeEndTime > :start")
    List<ClothCart> findOverlap(@Param("vatId") Long vatId,
                                @Param("cartId") Long cartId,
                                @Param("start") LocalDateTime start,
                                @Param("end") LocalDateTime end);

    @Query("select c from ClothCart c where c.vatId = :vatId and c.status = 'DYEING'")
    List<ClothCart> findDyeingByVat(@Param("vatId") Long vatId);

    @Query("select c from ClothCart c where c.status = :status order by c.cartCode")
    List<ClothCart> findByStatus(@Param("status") String status);
}
