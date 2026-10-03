package com.dyeing.mill.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dyeing.mill.domain.FabricProfile;

public interface FabricProfileRepository extends JpaRepository<FabricProfile, Long> {

    Optional<FabricProfile> findByFabricType(String fabricType);

    /**
     * 手写 JPQL ⑥：布种工艺档（升温速率上限），按上限升序。
     */
    @Query("select f from FabricProfile f order by f.maxRampRate asc")
    List<FabricProfile> findAllOrderByRampRate();
}
