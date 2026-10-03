package com.dyeing.mill.dto;

import java.math.BigDecimal;

/**
 * 布车状态流转入参（拖拽看板直接改状态）。
 */
public class CartStatusRequest {

    /** PENDING / DYEING / DONE / REWORK / SCRAPPED */
    private String status;

    private BigDecimal meters;

    private Integer reworkTimes;

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public BigDecimal getMeters() {
        return meters;
    }

    public void setMeters(BigDecimal meters) {
        this.meters = meters;
    }

    public Integer getReworkTimes() {
        return reworkTimes;
    }

    public void setReworkTimes(Integer reworkTimes) {
        this.reworkTimes = reworkTimes;
    }
}
