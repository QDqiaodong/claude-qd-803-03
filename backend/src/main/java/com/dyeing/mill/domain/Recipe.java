package com.dyeing.mill.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Data;
import org.hibernate.annotations.SQLRestriction;

/**
 * 染色配方：色号、客户、布种、LAB 与成本核算。明细行（染料/助剂）按序号有序维护。
 */
@Data
@Entity
@Table(name = "recipe")
@SQLRestriction("deleted = 0")
public class Recipe {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "recipe_code", nullable = false, length = 32)
    private String recipeCode;

    @Column(name = "color_code", length = 32)
    private String colorCode;

    @Column(name = "color_name", length = 64)
    private String colorName;

    @Column(name = "customer", length = 64)
    private String customer;

    @Column(name = "fabric_type", length = 40)
    private String fabricType;

    /** 配方版本（同一色号可有多版） */
    @Column(name = "version_no")
    private Integer versionNo;

    /** 目标色卡 CSS 色值 */
    @Column(name = "color_hex", length = 9)
    private String colorHex;

    @Column(name = "lab_l", precision = 6, scale = 2)
    private BigDecimal labL;

    @Column(name = "lab_a", precision = 6, scale = 2)
    private BigDecimal labA;

    @Column(name = "lab_b", precision = 6, scale = 2)
    private BigDecimal labB;

    /** DRAFT 草稿 / RELEASED 已放行 / ARCHIVED 归档 */
    @Column(name = "status", length = 20)
    private String status;

    /** 染料 owf 合计（应为 100.00） */
    @Column(name = "total_owf", precision = 7, scale = 3)
    private BigDecimal totalOwf;

    /** 每公斤布染化料成本（元/kg） */
    @Column(name = "cost_per_kg", precision = 10, scale = 2)
    private BigDecimal costPerKg;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @OneToMany(mappedBy = "recipe", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("seqNo ASC")
    private List<RecipeItem> items = new ArrayList<>();

    @Column(name = "deleted")
    private Integer deleted = 0;
}
