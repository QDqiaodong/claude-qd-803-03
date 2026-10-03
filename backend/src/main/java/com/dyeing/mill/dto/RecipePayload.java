package com.dyeing.mill.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 配方保存入参（表头 + 明细行）。
 */
public class RecipePayload {

    private String recipeCode;
    private String colorCode;
    private String colorName;
    private String customer;
    private String fabricType;
    private Integer versionNo;
    private String colorHex;
    private BigDecimal labL;
    private BigDecimal labA;
    private BigDecimal labB;
    private String status;
    private List<RecipeItemInput> items = new ArrayList<>();

    public String getRecipeCode() {
        return recipeCode;
    }

    public void setRecipeCode(String recipeCode) {
        this.recipeCode = recipeCode;
    }

    public String getColorCode() {
        return colorCode;
    }

    public void setColorCode(String colorCode) {
        this.colorCode = colorCode;
    }

    public String getColorName() {
        return colorName;
    }

    public void setColorName(String colorName) {
        this.colorName = colorName;
    }

    public String getCustomer() {
        return customer;
    }

    public void setCustomer(String customer) {
        this.customer = customer;
    }

    public String getFabricType() {
        return fabricType;
    }

    public void setFabricType(String fabricType) {
        this.fabricType = fabricType;
    }

    public Integer getVersionNo() {
        return versionNo;
    }

    public void setVersionNo(Integer versionNo) {
        this.versionNo = versionNo;
    }

    public String getColorHex() {
        return colorHex;
    }

    public void setColorHex(String colorHex) {
        this.colorHex = colorHex;
    }

    public BigDecimal getLabL() {
        return labL;
    }

    public void setLabL(BigDecimal labL) {
        this.labL = labL;
    }

    public BigDecimal getLabA() {
        return labA;
    }

    public void setLabA(BigDecimal labA) {
        this.labA = labA;
    }

    public BigDecimal getLabB() {
        return labB;
    }

    public void setLabB(BigDecimal labB) {
        this.labB = labB;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public List<RecipeItemInput> getItems() {
        return items;
    }

    public void setItems(List<RecipeItemInput> items) {
        this.items = items;
    }
}
