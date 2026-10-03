package com.dyeing.mill.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import org.hibernate.annotations.SQLRestriction;

/**
 * 布车流转：布车号、布种、米数、色号、所在缸、状态。
 * 报废布车（SCRAPPED）由 @SQLRestriction 全局过滤，看板与统计都看不到。
 */
@Data
@Entity
@Table(name = "cloth_cart")
@SQLRestriction("deleted = 0 and status <> 'SCRAPPED'")
public class ClothCart {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cart_code", nullable = false, length = 32)
    private String cartCode;

    @Column(name = "fabric_type", length = 40)
    private String fabricType;

    /** 米数 */
    @Column(name = "meters", precision = 10, scale = 1)
    private BigDecimal meters;

    @Column(name = "color_code", length = 32)
    private String colorCode;

    @Column(name = "color_name", length = 64)
    private String colorName;

    @Column(name = "customer", length = 64)
    private String customer;

    /** 所在染缸 */
    @Column(name = "vat_id")
    private Long vatId;

    @Column(name = "vat_code", length = 32)
    private String vatCode;

    /** PENDING 待排产 / DYEING 在染 / DONE 已完成 / REWORK 返修 / SCRAPPED 报废 */
    @Column(name = "status", length = 20)
    private String status;

    @Column(name = "dye_start_time")
    private LocalDateTime dyeStartTime;

    @Column(name = "dye_end_time")
    private LocalDateTime dyeEndTime;

    /** 客户交期（换色排程台维护，仅排程参考，不改布车流转状态） */
    @Column(name = "due_time")
    private LocalDateTime dueTime;

    /** 急单标记：0 普通 / 1 急单（排程时优先保交期，允许以清洗成本换时间） */
    @Column(name = "rush_flag")
    private Integer rushFlag = 0;

    /** 返修次数 */
    @Column(name = "rework_times")
    private Integer reworkTimes;

    @Column(name = "deleted")
    private Integer deleted = 0;
}
