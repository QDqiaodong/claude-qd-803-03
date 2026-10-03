package com.dyeing.mill.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Data;
import org.hibernate.annotations.SQLRestriction;

/**
 * 工艺曲线：升温段 → 保温段 → 降温段，节点序列作为有序明细行保存。
 */
@Data
@Entity
@Table(name = "process_curve")
@SQLRestriction("deleted = 0")
public class ProcessCurve {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "curve_code", nullable = false, length = 32)
    private String curveCode;

    @Column(name = "curve_name", length = 64)
    private String curveName;

    @Column(name = "fabric_type", length = 40)
    private String fabricType;

    @Column(name = "color_code", length = 32)
    private String colorCode;

    /** 关联配方 */
    @Column(name = "recipe_id")
    private Long recipeId;

    /** 起始温度 ℃ */
    @Column(name = "start_temp", precision = 5, scale = 1)
    private BigDecimal startTemp;

    /** 曲线总时长（分钟） */
    @Column(name = "total_minutes")
    private Integer totalMinutes;

    /** 全曲线最大升温速率 ℃/min */
    @Column(name = "max_ramp_rate", precision = 5, scale = 2)
    private BigDecimal maxRampRate;

    @OneToMany(mappedBy = "curve", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("seqNo ASC")
    private List<CurveNode> nodes = new ArrayList<>();

    @Column(name = "deleted")
    private Integer deleted = 0;
}
