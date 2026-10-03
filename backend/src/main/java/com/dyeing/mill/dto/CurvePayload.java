package com.dyeing.mill.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 工艺曲线保存入参（表头 + 有序节点序列）。
 */
public class CurvePayload {

    private String curveCode;
    private String curveName;
    private String fabricType;
    private String colorCode;
    private Long recipeId;
    private BigDecimal startTemp;
    private List<CurveNodeInput> nodes = new ArrayList<>();

    public String getCurveCode() {
        return curveCode;
    }

    public void setCurveCode(String curveCode) {
        this.curveCode = curveCode;
    }

    public String getCurveName() {
        return curveName;
    }

    public void setCurveName(String curveName) {
        this.curveName = curveName;
    }

    public String getFabricType() {
        return fabricType;
    }

    public void setFabricType(String fabricType) {
        this.fabricType = fabricType;
    }

    public String getColorCode() {
        return colorCode;
    }

    public void setColorCode(String colorCode) {
        this.colorCode = colorCode;
    }

    public Long getRecipeId() {
        return recipeId;
    }

    public void setRecipeId(Long recipeId) {
        this.recipeId = recipeId;
    }

    public BigDecimal getStartTemp() {
        return startTemp;
    }

    public void setStartTemp(BigDecimal startTemp) {
        this.startTemp = startTemp;
    }

    public List<CurveNodeInput> getNodes() {
        return nodes;
    }

    public void setNodes(List<CurveNodeInput> nodes) {
        this.nodes = nodes;
    }
}
