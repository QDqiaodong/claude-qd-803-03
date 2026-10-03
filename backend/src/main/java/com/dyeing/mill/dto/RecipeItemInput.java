package com.dyeing.mill.dto;

import java.math.BigDecimal;

/**
 * 配方明细行入参（前端阶梯表行内编辑直接提交这个结构）。
 */
public class RecipeItemInput {

    private Long id;

    /** DYE 染料 / AUX 助剂 */
    private String itemType;

    private String dyeName;

    private BigDecimal owfPercent;

    private BigDecimal unitPrice;

    private String colorHex;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getItemType() {
        return itemType;
    }

    public void setItemType(String itemType) {
        this.itemType = itemType;
    }

    public String getDyeName() {
        return dyeName;
    }

    public void setDyeName(String dyeName) {
        this.dyeName = dyeName;
    }

    public BigDecimal getOwfPercent() {
        return owfPercent;
    }

    public void setOwfPercent(BigDecimal owfPercent) {
        this.owfPercent = owfPercent;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }

    public String getColorHex() {
        return colorHex;
    }

    public void setColorHex(String colorHex) {
        this.colorHex = colorHex;
    }
}
