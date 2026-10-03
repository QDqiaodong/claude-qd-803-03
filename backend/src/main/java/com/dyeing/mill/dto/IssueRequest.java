package com.dyeing.mill.dto;

import java.math.BigDecimal;

/**
 * 染料/助剂领用入参。
 */
public class IssueRequest {

    private Long stockId;
    private BigDecimal qtyKg;
    private String operator;
    private String recipeCode;
    private String cartCode;

    public Long getStockId() {
        return stockId;
    }

    public void setStockId(Long stockId) {
        this.stockId = stockId;
    }

    public BigDecimal getQtyKg() {
        return qtyKg;
    }

    public void setQtyKg(BigDecimal qtyKg) {
        this.qtyKg = qtyKg;
    }

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }

    public String getRecipeCode() {
        return recipeCode;
    }

    public void setRecipeCode(String recipeCode) {
        this.recipeCode = recipeCode;
    }

    public String getCartCode() {
        return cartCode;
    }

    public void setCartCode(String cartCode) {
        this.cartCode = cartCode;
    }
}
