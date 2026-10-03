package com.dyeing.mill.domain;

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
 * 清洗规则版本：每次矩阵调整发布新版本，旧方案按当时版本复算并显示原解释。
 * 规则单元通过 WashRuleRepository 单独加载（@Transient），避免复合外键级联。
 */
@Data
@Entity
@Table(name = "wash_rule_version")
@SQLRestriction("deleted = 0")
public class WashRuleVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "version_no", nullable = false)
    private Integer versionNo;

    /** DRAFT / ACTIVE / ARCHIVED */
    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "change_note", length = 300)
    private String changeNote;

    @Column(name = "created_by", length = 32)
    private String createdBy;

    @Column(name = "create_time", nullable = false)
    private LocalDateTime createTime;

    @Column(name = "publish_time")
    private LocalDateTime publishTime;

    /** 矩阵单元由 Service 显式装载（不建 JPA 关联）。 */
    @Transient
    private java.util.List<WashRule> rules = new java.util.ArrayList<>();

    @Column(name = "deleted")
    private Integer deleted = 0;
}
