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
 * 染缸台账：缸号、机台类型（溢喷/气流/卷染）、容量、浴比、当前状态与温度。
 * 软删除过滤交给 Hibernate 6 的 @SQLRestriction，所有查询天然看不到已删除的缸。
 */
@Data
@Entity
@Table(name = "dye_vat")
@SQLRestriction("deleted = 0")
public class DyeVat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 缸号，如 V-01 */
    @Column(name = "vat_code", nullable = false, length = 32)
    private String vatCode;

    /** 机台类型：OVERFLOW 溢喷 / AIRFLOW 气流 / JIG 卷染 */
    @Column(name = "machine_type", nullable = false, length = 20)
    private String machineType;

    /** 容量（kg） */
    @Column(name = "capacity_kg", precision = 10, scale = 2)
    private BigDecimal capacityKg;

    /** 浴比，如 8.00 表示 1:8 */
    @Column(name = "liquor_ratio", precision = 5, scale = 2)
    private BigDecimal liquorRatio;

    /** 状态：IDLE 空闲 / HEATING 升温 / HOLDING 保温 / COOLING 降温 / DRAINING 排液 / WASHING 清洗 */
    @Column(name = "status", length = 20)
    private String status;

    /** 当前温度 ℃ */
    @Column(name = "current_temp", precision = 5, scale = 1)
    private BigDecimal currentTemp;

    /** 目标温度 ℃ */
    @Column(name = "target_temp", precision = 5, scale = 1)
    private BigDecimal targetTemp;

    /** 保温时长（分钟） */
    @Column(name = "hold_minutes")
    private Integer holdMinutes;

    /** 保温剩余时长（分钟） */
    @Column(name = "hold_remain_minutes")
    private Integer holdRemainMinutes;

    /** 所在车间区域 */
    @Column(name = "workshop", length = 32)
    private String workshop;

    @Column(name = "remark", length = 200)
    private String remark;

    @Column(name = "deleted")
    private Integer deleted = 0;
}
