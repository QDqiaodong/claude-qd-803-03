package com.dyeing.mill.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import org.hibernate.annotations.SQLRestriction;

/**
 * 色号 → 颜色族映射：布车排程时据此解析所属颜色族。
 */
@Data
@Entity
@Table(name = "color_family_map")
@SQLRestriction("deleted = 0")
public class ColorFamilyMap {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "color_code", nullable = false, length = 32)
    private String colorCode;

    @Column(name = "family_id", nullable = false)
    private Long familyId;

    @Column(name = "deleted")
    private Integer deleted = 0;
}
