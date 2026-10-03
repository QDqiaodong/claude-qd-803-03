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
 * 染缸维护时段：排程必须绕开的固定不可用区间。
 */
@Data
@Entity
@Table(name = "maintenance_window")
@SQLRestriction("deleted = 0")
public class MaintenanceWindow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "vat_id", nullable = false)
    private Long vatId;

    @Column(name = "vat_code", nullable = false, length = 32)
    private String vatCode;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalDateTime endTime;

    @Column(name = "reason", nullable = false, length = 200)
    private String reason;

    @Column(name = "created_by", length = 32)
    private String createdBy;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Column(name = "deleted")
    private Integer deleted = 0;
}
