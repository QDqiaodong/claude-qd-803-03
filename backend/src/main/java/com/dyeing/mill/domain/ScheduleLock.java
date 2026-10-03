package com.dyeing.mill.domain;

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
 * 排程人工锁定段：调度员对已承诺缸位的锁定。
 * 重算只能调整未锁定的未来区段，锁定段（含仅封缸段）原样保留。
 */
@Data
@Entity
@Table(name = "schedule_lock")
@SQLRestriction("deleted = 0")
public class ScheduleLock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "vat_id", nullable = false)
    private Long vatId;

    @Column(name = "vat_code", nullable = false, length = 32)
    private String vatCode;

    /** 锁定的布车；为空表示仅封缸（该时段不排任何车） */
    @Column(name = "cart_id")
    private Long cartId;

    @Column(name = "cart_code", length = 32)
    private String cartCode;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalDateTime endTime;

    @Column(name = "lock_reason", nullable = false, length = 200)
    private String lockReason;

    @Column(name = "created_by", length = 32)
    private String createdBy;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Column(name = "deleted")
    private Integer deleted = 0;
}
