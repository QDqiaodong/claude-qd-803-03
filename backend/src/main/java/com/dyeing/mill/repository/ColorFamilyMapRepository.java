package com.dyeing.mill.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dyeing.mill.domain.ColorFamilyMap;

public interface ColorFamilyMapRepository extends JpaRepository<ColorFamilyMap, Long> {

    Optional<ColorFamilyMap> findByColorCode(String colorCode);
}
