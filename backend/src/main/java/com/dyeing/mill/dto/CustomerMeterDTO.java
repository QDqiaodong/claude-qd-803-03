package com.dyeing.mill.dto;

import java.math.BigDecimal;

/**
 * 按客户汇总米数（JPQL 联表分组统计结果）。
 */
public class CustomerMeterDTO {

    private String customer;
    private Long batchCount;
    private BigDecimal totalMeters;

    public CustomerMeterDTO(String customer, Long batchCount, BigDecimal totalMeters) {
        this.customer = customer;
        this.batchCount = batchCount;
        this.totalMeters = totalMeters;
    }

    public String getCustomer() {
        return customer;
    }

    public Long getBatchCount() {
        return batchCount;
    }

    public BigDecimal getTotalMeters() {
        return totalMeters;
    }
}
