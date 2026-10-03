package com.dyeing.mill.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

/**
 * 开缸执行单：待确认阶段只保存来源指针与资料指纹；下达时才写入不可变工艺快照。
 */
@Data
@Entity
@Table(name = "vat_execution_order")
public class ExecutionOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_code", nullable = false, length = 32)
    private String orderCode;

    /** PENDING_CONFIRM 待确认 / ISSUED 已下达 / INVALID 已失效 */
    @Column(name = "status", nullable = false, length = 24)
    private String status;

    @Column(name = "cart_id", nullable = false)
    private Long cartId;

    @Column(name = "cart_code", nullable = false, length = 32)
    private String cartCode;

    @Column(name = "vat_id", nullable = false)
    private Long vatId;

    @Column(name = "vat_code", nullable = false, length = 32)
    private String vatCode;

    @Column(name = "recipe_id", nullable = false)
    private Long recipeId;

    @Column(name = "recipe_code", nullable = false, length = 32)
    private String recipeCode;

    @Column(name = "recipe_version_no", nullable = false)
    private Integer recipeVersionNo;

    @Column(name = "curve_id", nullable = false)
    private Long curveId;

    @Column(name = "curve_code", nullable = false, length = 32)
    private String curveCode;

    /** 创建时来源资料的校验指纹；待确认单每次读取都会按最新资料重新核对。 */
    @Column(name = "source_revision", nullable = false, length = 64)
    private String sourceRevision;

    @Column(name = "estimated_weight_kg", precision = 10, scale = 2)
    private java.math.BigDecimal estimatedWeightKg;

    @Column(name = "estimated_minutes")
    private Integer estimatedMinutes;

    @Column(name = "key_temp", precision = 5, scale = 1)
    private java.math.BigDecimal keyTemp;

    @Column(name = "target_temp", precision = 5, scale = 1)
    private java.math.BigDecimal targetTemp;

    @Column(name = "hold_minutes")
    private Integer holdMinutes;

    @Column(name = "machine_max_temp", precision = 5, scale = 1)
    private java.math.BigDecimal machineMaxTemp;

    /** 下达瞬间的完整工艺快照（布车、染缸、配方、曲线节点、关键参数）。 */
    @Column(name = "snapshot_json", columnDefinition = "LONGTEXT")
    private String snapshotJson;

    @Column(name = "invalid_reason", length = 500)
    private String invalidReason;

    @Column(name = "created_by", length = 32)
    private String createdBy;

    @Column(name = "confirmed_by", length = 32)
    private String confirmedBy;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Column(name = "issue_time")
    private LocalDateTime issueTime;
}
