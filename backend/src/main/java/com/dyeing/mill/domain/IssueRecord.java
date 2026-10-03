package com.dyeing.mill.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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

/**
 * 染料/助剂领用记录。
 */
@Data
@Entity
@Table(name = "issue_record")
public class IssueRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_id")
    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private ChemicalStock stock;

    @Column(name = "chem_code", length = 32)
    private String chemCode;

    @Column(name = "chem_name", length = 64)
    private String chemName;

    @Column(name = "qty_kg", precision = 10, scale = 2)
    private BigDecimal qtyKg;

    @Column(name = "operator", length = 32)
    private String operator;

    @Column(name = "recipe_code", length = 32)
    private String recipeCode;

    @Column(name = "cart_code", length = 32)
    private String cartCode;

    @Column(name = "issue_time")
    private LocalDateTime issueTime;

    @Column(name = "deleted")
    private Integer deleted = 0;
}
