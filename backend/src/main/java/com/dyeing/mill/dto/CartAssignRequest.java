package com.dyeing.mill.dto;

/**
 * 布车排缸（落入「在染」列）入参：染缸 + 计划起止时间。
 */
public class CartAssignRequest {

    private Long vatId;
    private String startTime;
    private String endTime;

    public Long getVatId() {
        return vatId;
    }

    public void setVatId(Long vatId) {
        this.vatId = vatId;
    }

    public String getStartTime() {
        return startTime;
    }

    public void setStartTime(String startTime) {
        this.startTime = startTime;
    }

    public String getEndTime() {
        return endTime;
    }

    public void setEndTime(String endTime) {
        this.endTime = endTime;
    }
}
