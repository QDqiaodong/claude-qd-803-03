package com.dyeing.mill.dto;

/**
 * 排程台维护布车交期/急单：只改这两个排程字段，绝不触碰布车状态与排缸结果。
 */
public class CartSchedulePatchRequest {

    /** yyyy-MM-dd HH:mm，空串表示清除交期 */
    private String dueTime;
    /** 0 普通 / 1 急单 */
    private Integer rushFlag;

    public String getDueTime() {
        return dueTime;
    }

    public void setDueTime(String dueTime) {
        this.dueTime = dueTime;
    }

    public Integer getRushFlag() {
        return rushFlag;
    }

    public void setRushFlag(Integer rushFlag) {
        this.rushFlag = rushFlag;
    }
}
