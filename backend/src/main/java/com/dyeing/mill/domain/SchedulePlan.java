package com.dyeing.mill.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.Data;
import org.hibernate.annotations.SQLRestriction;

/**
 * 换色排程方案：一次跨缸重算的完整产物，可留存多份、选定一份作车间参考。
 * 不回写布车、不开执行单；inputSnapshotJson 保存生成时全量输入，
 * 规则版本升级后旧方案仍可按当时快照复算并显示原解释。
 */
@Data
@Entity
@Table(name = "schedule_plan")
@SQLRestriction("deleted = 0")
public class SchedulePlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "plan_code", nullable = false, length = 32)
    private String planCode;

    @Column(name = "plan_name", nullable = false, length = 64)
    private String planName;

    /** DRAFT 草稿 / REFERENCE 车间参考 / ARCHIVED 归档（同表仅一个 REFERENCE，由生成列唯一索引保证） */
    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "rule_version_id", nullable = false)
    private Long ruleVersionId;

    @Column(name = "rule_version_no", nullable = false)
    private Integer ruleVersionNo;

    @Column(name = "horizon_start_time", nullable = false)
    private LocalDateTime horizonStartTime;

    @Column(name = "horizon_end_time", nullable = false)
    private LocalDateTime horizonEndTime;

    /** NOW 按当前数据生成 / REPLAY 按历史快照复算 */
    @Column(name = "anchor_mode", nullable = false, length = 16)
    private String anchorMode = "NOW";

    @Column(name = "input_snapshot_json", nullable = false, columnDefinition = "LONGTEXT")
    private String inputSnapshotJson;

    @Column(name = "total_carts", nullable = false)
    private Integer totalCarts = 0;

    @Column(name = "scheduled_carts", nullable = false)
    private Integer scheduledCarts = 0;

    @Column(name = "excluded_carts", nullable = false)
    private Integer excludedCarts = 0;

    @Column(name = "late_carts", nullable = false)
    private Integer lateCarts = 0;

    @Column(name = "total_wash_minutes", nullable = false)
    private Integer totalWashMinutes = 0;

    @Column(name = "total_wash_cost", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalWashCost = BigDecimal.ZERO;

    @Column(name = "total_idle_minutes", nullable = false)
    private Integer totalIdleMinutes = 0;

    @Column(name = "total_late_minutes", nullable = false)
    private Integer totalLateMinutes = 0;

    @Column(name = "created_by", length = 32)
    private String createdBy;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Column(name = "reference_time")
    private LocalDateTime referenceTime;

    /** 方案明细由 Service 通过 SchedulePlanAssignmentRepository 显式装载（不建 JPA 关联）。 */
    @Transient
    private java.util.List<SchedulePlanAssignment> assignments = new java.util.ArrayList<>();

    @Column(name = "deleted")
    private Integer deleted = 0;
}
