package com.dyeing.mill.dto;

import java.math.BigDecimal;

/**
 * 控温面板提交参数：目标温度 + 保温时长。
 */
public class VatControlDTO {

    private BigDecimal targetTemp;

    private Integer holdMinutes;

    public BigDecimal getTargetTemp() {
        return targetTemp;
    }

    public void setTargetTemp(BigDecimal targetTemp) {
        this.targetTemp = targetTemp;
    }

    public Integer getHoldMinutes() {
        return holdMinutes;
    }

    public void setHoldMinutes(Integer holdMinutes) {
        this.holdMinutes = holdMinutes;
    }
}
