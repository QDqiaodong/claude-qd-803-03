package com.dyeing.mill.dto;

import java.math.BigDecimal;

/**
 * 清洗矩阵单元编辑行（源族 → 目标族的清洗时长/成本/类型）。
 */
public class WashRuleCellInput {

    private Long fromFamilyId;
    private Long toFamilyId;
    private String switchType;
    private Integer washMinutes;
    private BigDecimal washCost;
    private String remark;

    public Long getFromFamilyId() {
        return fromFamilyId;
    }

    public void setFromFamilyId(Long fromFamilyId) {
        this.fromFamilyId = fromFamilyId;
    }

    public Long getToFamilyId() {
        return toFamilyId;
    }

    public void setToFamilyId(Long toFamilyId) {
        this.toFamilyId = toFamilyId;
    }

    public String getSwitchType() {
        return switchType;
    }

    public void setSwitchType(String switchType) {
        this.switchType = switchType;
    }

    public Integer getWashMinutes() {
        return washMinutes;
    }

    public void setWashMinutes(Integer washMinutes) {
        this.washMinutes = washMinutes;
    }

    public BigDecimal getWashCost() {
        return washCost;
    }

    public void setWashCost(BigDecimal washCost) {
        this.washCost = washCost;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
