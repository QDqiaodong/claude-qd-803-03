package com.dyeing.mill.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import org.hibernate.annotations.SQLRestriction;

/**
 * 布种 × 机型适配：排程只允许把布车排到适配机型的染缸。
 * 未配置任何适配机型的布种，其布车全部按“机型不适配”排除。
 */
@Data
@Entity
@Table(name = "fabric_machine")
@SQLRestriction("deleted = 0")
public class FabricMachine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "fabric_type", nullable = false, length = 40)
    private String fabricType;

    /** OVERFLOW / AIRFLOW / JIG */
    @Column(name = "machine_type", nullable = false, length = 20)
    private String machineType;

    /** 是否首选机型（评分时同条件优先排首选缸） */
    @Column(name = "preferred", nullable = false)
    private Integer preferred = 0;

    @Column(name = "deleted")
    private Integer deleted = 0;
}
