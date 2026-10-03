package com.dyeing.mill.domain;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.hibernate.annotations.SQLRestriction;

/**
 * 配方明细行：染料名称、owf 百分比、单价；助剂（匀染剂/固色剂/盐/碱）同样作为明细行。
 * 由 Recipe.items 的 @OrderBy("seqNo ASC") 保证行序。
 */
@Data
@Entity
@Table(name = "recipe_item")
@SQLRestriction("deleted = 0")
public class RecipeItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipe_id")
    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Recipe recipe;

    /** 行序号 */
    @Column(name = "seq_no")
    private Integer seqNo;

    /** DYE 染料 / AUX 助剂 */
    @Column(name = "item_type", length = 10)
    private String itemType;

    @Column(name = "dye_name", length = 64)
    private String dyeName;

    /** owf 百分比，染料行合计须为 100 */
    @Column(name = "owf_percent", precision = 8, scale = 3)
    private BigDecimal owfPercent;

    /** 单价（元/kg） */
    @Column(name = "unit_price", precision = 10, scale = 2)
    private BigDecimal unitPrice;

    /** 该染料/助剂单独的色阶色值（用于阶梯表的浅→深小条） */
    @Column(name = "color_hex", length = 9)
    private String colorHex;

    @Column(name = "deleted")
    private Integer deleted = 0;
}
