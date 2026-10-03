package com.dyeing.mill.dto;

/**
 * 换色排程：生成方案入参。
 * anchorTime 仅用于历史复算/测试，正常生成不传（服务端取当前时间）。
 */
public class ScheduleGenerateRequest {

    private String planName;
    private String createdBy;
    /** 排程视窗长度（小时），默认 120（5 天） */
    private Integer horizonHours;
    private String anchorTime;

    public String getPlanName() {
        return planName;
    }

    public void setPlanName(String planName) {
        this.planName = planName;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public Integer getHorizonHours() {
        return horizonHours;
    }

    public void setHorizonHours(Integer horizonHours) {
        this.horizonHours = horizonHours;
    }

    public String getAnchorTime() {
        return anchorTime;
    }

    public void setAnchorTime(String anchorTime) {
        this.anchorTime = anchorTime;
    }
}
