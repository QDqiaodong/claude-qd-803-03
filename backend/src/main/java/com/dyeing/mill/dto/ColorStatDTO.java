package com.dyeing.mill.dto;

import java.math.BigDecimal;

/**
 * 按色号统计缸次（JPQL 分组统计结果）。
 */
public class ColorStatDTO {

    private String colorCode;
    private String colorName;
    private Long batchCount;
    private BigDecimal totalMeters;

    public ColorStatDTO(String colorCode, String colorName, Long batchCount, BigDecimal totalMeters) {
        this.colorCode = colorCode;
        this.colorName = colorName;
        this.batchCount = batchCount;
        this.totalMeters = totalMeters;
    }

    public String getColorCode() {
        return colorCode;
    }

    public String getColorName() {
        return colorName;
    }

    public Long getBatchCount() {
        return batchCount;
    }

    public BigDecimal getTotalMeters() {
        return totalMeters;
    }
}
