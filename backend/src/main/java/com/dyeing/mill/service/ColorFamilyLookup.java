package com.dyeing.mill.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dyeing.mill.domain.ColorFamily;
import com.dyeing.mill.repository.ColorFamilyRepository;

/** 颜色族只读查询（控制器薄封装）。 */
@Service
public class ColorFamilyLookup {

    private final ColorFamilyRepository familyRepository;

    public ColorFamilyLookup(ColorFamilyRepository familyRepository) {
        this.familyRepository = familyRepository;
    }

    @Transactional(readOnly = true)
    public List<ColorFamily> all() {
        return familyRepository.findAllOrdered();
    }
}
