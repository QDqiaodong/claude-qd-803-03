package com.dyeing.mill.domain;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * 工艺曲线节点：一个节点就是曲线上的一个拐点（升温/保温/降温段）。
 */
@Data
@Entity
@Table(name = "curve_node")
public class CurveNode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "curve_id")
    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private ProcessCurve curve;

    @Column(name = "seq_no")
    private Integer seqNo;

    /** RAMP 升温 / HOLD 保温 / COOL 降温 */
    @Column(name = "stage", length = 10)
    private String stage;

    /** 节点目标温度 ℃ */
    @Column(name = "target_temp", precision = 5, scale = 1)
    private BigDecimal targetTemp;

    /** 本段时长（分钟） */
    @Column(name = "duration_min")
    private Integer durationMin;

    /** 本段速率 ℃/min（升温为正、降温为负） */
    @Column(name = "ramp_rate", precision = 5, scale = 2)
    private BigDecimal rampRate;

    @Column(name = "memo", length = 100)
    private String memo;
}
