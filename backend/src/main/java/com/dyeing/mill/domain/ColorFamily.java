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
 * 颜色族：深浅层级（同色/浅/深/特殊）+ 染料类，是清洗矩阵的行列维度。
 */
@Data
@Entity
@Table(name = "color_family")
@SQLRestriction("deleted = 0")
public class ColorFamily {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "family_code", nullable = false, length = 32)
    private String familyCode;

    @Column(name = "family_name", nullable = false, length = 48)
    private String familyName;

    /** SAME 同色 / LIGHT 浅 / DARK 深 / SPECIAL 特殊染料 */
    @Column(name = "depth_level", nullable = false, length = 10)
    private String depthLevel;

    /** REACTIVE / DISPERSE / ACID / CATIONIC / BRIGHT / NEUTRAL */
    @Column(name = "dye_class", nullable = false, length = 20)
    private String dyeClass;

    @Column(name = "color_hex", nullable = false, length = 9)
    private String colorHex;

    @Column(name = "remark", length = 200)
    private String remark;

    @Column(name = "deleted")
    private Integer deleted = 0;
}
