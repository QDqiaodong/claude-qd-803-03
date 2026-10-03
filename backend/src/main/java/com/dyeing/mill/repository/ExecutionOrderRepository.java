package com.dyeing.mill.repository;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dyeing.mill.domain.ExecutionOrder;

public interface ExecutionOrderRepository extends JpaRepository<ExecutionOrder, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from ExecutionOrder o where o.id = :id")
    Optional<ExecutionOrder> findByIdForUpdate(@Param("id") Long id);

    @Query("select o from ExecutionOrder o where o.cartId = :cartId and o.status = 'ISSUED'")
    List<ExecutionOrder> findIssuedByCart(@Param("cartId") Long cartId);

    @Query("select o from ExecutionOrder o where o.cartId = :cartId and o.status in ('PENDING_CONFIRM', 'ISSUED') order by o.createTime desc")
    List<ExecutionOrder> findActiveByCart(@Param("cartId") Long cartId);

    @Query("select o from ExecutionOrder o where o.recipeId = :recipeId and o.status = 'PENDING_CONFIRM' order by o.id")
    List<ExecutionOrder> findPendingByRecipe(@Param("recipeId") Long recipeId);

    @Query("select o from ExecutionOrder o where o.curveId = :curveId and o.status = 'PENDING_CONFIRM' order by o.id")
    List<ExecutionOrder> findPendingByCurve(@Param("curveId") Long curveId);

    @Query("select o from ExecutionOrder o where o.cartId = :cartId and o.status = 'PENDING_CONFIRM' order by o.id")
    List<ExecutionOrder> findPendingByCart(@Param("cartId") Long cartId);

    @Query("select o from ExecutionOrder o order by case o.status when 'PENDING_CONFIRM' then 0 when 'ISSUED' then 1 else 2 end, o.createTime desc")
    List<ExecutionOrder> findAllOrderByStatus();
}
