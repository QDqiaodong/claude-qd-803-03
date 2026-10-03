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

/**
 * 方案布车排程明细：每辆布车为何被安排、延后或排除，都写在 reasonText。
 * resultType：SCHEDULED 自动排入 / EXCLUDED 资料或容量等原因排除 / FIXED 在制或锁定的固定段。
 */
@Data
@Entity
@Table(name = "schedule_plan_assignment")
public class SchedulePlanAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "plan_id", nullable = false)
    private Long planId;

    @Column(name = "cart_id", nullable = false)
    private Long cartId;

    @Column(name = "cart_code", nullable = false, length = 32)
    private String cartCode;

    /** SCHEDULED / EXCLUDED / FIXED */
    @Column(name = "result_type", nullable = false, length = 12)
    private String resultType;

    @Column(name = "vat_id")
    private Long vatId;

    @Column(name = "vat_code", length = 32)
    private String vatCode;

    /** 染色开始（清洗结束之后） */
    @Column(name = "start_time")
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    /** 入缸前清洗时长（与上一缸色族之间的矩阵取值） */
    @Column(name = "wash_before_min")
    private Integer washBeforeMin;

    @Column(name = "wash_before_cost", precision = 10, scale = 2)
    private BigDecimal washBeforeCost;

    /** 清洗来源（上一缸）布车；首缸中性起步为 null */
    @Column(name = "wash_from_cart_id")
    private Long washFromCartId;

    /** SAME / L2D / D2L / SPECIAL */
    @Column(name = "switch_type", length = 10)
    private String switchType;

    @Column(name = "family_code", length = 32)
    private String familyCode;

    @Column(name = "late_minutes", nullable = false)
    private Integer lateMinutes = 0;

    /** 人工锁定段标记：重算不得改动 */
    @Column(name = "locked", nullable = false)
    private Integer locked = 0;

    @Column(name = "reason_text", nullable = false, length = 500)
    private String reasonText;
}
