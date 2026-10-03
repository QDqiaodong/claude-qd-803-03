package com.dyeing.mill.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
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
 * 染料与助剂库存：库存 kg、安全库存、批次与效期；领用记录作为有序子集合。
 */
@Data
@Entity
@Table(name = "chemical_stock")
@SQLRestriction("deleted = 0")
public class ChemicalStock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "chem_code", nullable = false, length = 32)
    private String chemCode;

    @Column(name = "chem_name", length = 64)
    private String chemName;

    /** DYE 染料 / AUX 助剂 */
    @Column(name = "category", length = 10)
    private String category;

    @Column(name = "unit", length = 10)
    private String unit;

    @Column(name = "stock_kg", precision = 10, scale = 2)
    private BigDecimal stockKg;

    @Column(name = "safety_kg", precision = 10, scale = 2)
    private BigDecimal safetyKg;

    @Column(name = "batch_no", length = 32)
    private String batchNo;

    @Column(name = "expire_date")
    private LocalDate expireDate;

    @Column(name = "unit_price", precision = 10, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "supplier", length = 64)
    private String supplier;

    @OneToMany(mappedBy = "stock", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("issueTime DESC")
    private List<IssueRecord> issues = new ArrayList<>();

    @Column(name = "deleted")
    private Integer deleted = 0;
}
