package com.dyeing.mill.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 染料库存预警（JPQL 查询结果：库存低于安全库存的染化料）。
 */
public class StockAlertDTO {

    private Long id;
    private String chemCode;
    private String chemName;
    private String category;
    private BigDecimal stockKg;
    private BigDecimal safetyKg;
    private LocalDate expireDate;
    private BigDecimal gapKg;

    public StockAlertDTO(Long id, String chemCode, String chemName, String category,
                         BigDecimal stockKg, BigDecimal safetyKg, LocalDate expireDate) {
        this.id = id;
        this.chemCode = chemCode;
        this.chemName = chemName;
        this.category = category;
        this.stockKg = stockKg;
        this.safetyKg = safetyKg;
        this.expireDate = expireDate;
        this.gapKg = safetyKg == null || stockKg == null ? BigDecimal.ZERO : safetyKg.subtract(stockKg);
    }

    public Long getId() {
        return id;
    }

    public String getChemCode() {
        return chemCode;
    }

    public String getChemName() {
        return chemName;
    }

    public String getCategory() {
        return category;
    }

    public BigDecimal getStockKg() {
        return stockKg;
    }

    public BigDecimal getSafetyKg() {
        return safetyKg;
    }

    public LocalDate getExpireDate() {
        return expireDate;
    }

    public BigDecimal getGapKg() {
        return gapKg;
    }
}
