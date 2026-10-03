package com.dyeing.mill.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dyeing.mill.domain.Recipe;

public interface RecipeRepository extends JpaRepository<Recipe, Long> {

    /**
     * 手写 JPQL：同一色号下的配方版本，版本号倒序。
     */
    @Query("select r from Recipe r where r.colorCode = :colorCode order by r.versionNo desc")
    List<Recipe> findVersionsByColorCode(@Param("colorCode") String colorCode);

    /**
     * 手写 JPQL：按客户统计配方数与单公斤平均成本。
     */
    @Query("select r.customer, count(r), avg(r.costPerKg) from Recipe r where r.deleted = 0 group by r.customer order by count(r) desc")
    List<Object[]> statByCustomer();

    @Query("select r from Recipe r where r.customer = :customer order by r.recipeCode")
    List<Recipe> findByCustomer(@Param("customer") String customer);
}
