package com.dyeing.mill.domain;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import org.hibernate.annotations.SQLRestriction;

/**
 * 清洗矩阵单元：版本内「源颜色族 → 目标颜色族」所需清洗时长与成本。
 * switchType 标明同色续染 / 浅转深 / 深转浅 / 特殊切换。
 */
@Data
@Entity
@Table(name = "wash_rule")
@SQLRestriction("deleted = 0")
public class WashRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "version_id", nullable = false)
    private Long versionId;

    @Column(name = "from_family_id", nullable = false)
    private Long fromFamilyId;

    @Column(name = "to_family_id", nullable = false)
    private Long toFamilyId;

    /** SAME / L2D / D2L / SPECIAL */
    @Column(name = "switch_type", nullable = false, length = 10)
    private String switchType;

    /** 清洗时长（分钟） */
    @Column(name = "wash_minutes", nullable = false)
    private Integer washMinutes;

    /** 清洗成本（元：水电 + 清洗助剂） */
    @Column(name = "wash_cost", nullable = false, precision = 10, scale = 2)
    private BigDecimal washCost;

    @Column(name = "remark", length = 200)
    private String remark;

    @Column(name = "deleted")
    private Integer deleted = 0;
}
