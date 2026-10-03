package com.dyeing.mill.dto;

import java.math.BigDecimal;

/**
 * 工艺曲线节点入参：拖动 SVG 节点后提交温度与时长。
 */
public class CurveNodeInput {

    private Long id;

    /** RAMP 升温 / HOLD 保温 / COOL 降温 */
    private String stage;

    private BigDecimal targetTemp;

    private Integer durationMin;

    private String memo;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getStage() {
        return stage;
    }

    public void setStage(String stage) {
        this.stage = stage;
    }

    public BigDecimal getTargetTemp() {
        return targetTemp;
    }

    public void setTargetTemp(BigDecimal targetTemp) {
        this.targetTemp = targetTemp;
    }

    public Integer getDurationMin() {
        return durationMin;
    }

    public void setDurationMin(Integer durationMin) {
        this.durationMin = durationMin;
    }

    public String getMemo() {
        return memo;
    }

    public void setMemo(String memo) {
        this.memo = memo;
    }
}
