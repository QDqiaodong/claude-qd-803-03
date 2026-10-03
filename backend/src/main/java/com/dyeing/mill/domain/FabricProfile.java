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
 * 布种工艺档案：每个布种允许的升温速率上限、保温温度上限、最小浴比。
 * 工艺曲线保存时用它做速率拦截。
 */
@Data
@Entity
@Table(name = "fabric_profile")
@SQLRestriction("enabled = 1")
public class FabricProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 布种编码：COTTON / POLY / NYLON ... */
    @Column(name = "fabric_type", nullable = false, length = 40)
    private String fabricType;

    /** 布种名称 */
    @Column(name = "fabric_name", length = 64)
    private String fabricName;

    /** 允许的升温速率上限 ℃/min */
    @Column(name = "max_ramp_rate", precision = 5, scale = 2)
    private BigDecimal maxRampRate;

    /** 允许的保温温度上限 ℃ */
    @Column(name = "max_hold_temp", precision = 5, scale = 1)
    private BigDecimal maxHoldTemp;

    /** 最小浴比 */
    @Column(name = "min_liquor_ratio", precision = 5, scale = 2)
    private BigDecimal minLiquorRatio;

    @Column(name = "enabled")
    private Integer enabled = 1;
}
