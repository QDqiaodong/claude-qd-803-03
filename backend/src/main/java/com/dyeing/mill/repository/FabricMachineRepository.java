package com.dyeing.mill.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dyeing.mill.domain.FabricMachine;

public interface FabricMachineRepository extends JpaRepository<FabricMachine, Long> {

    List<FabricMachine> findByFabricType(String fabricType);
}
