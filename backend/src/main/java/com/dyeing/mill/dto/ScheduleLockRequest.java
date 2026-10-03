package com.dyeing.mill.dto;

/**
 * 人工锁定段（承诺缸位）入参。cartId 可空 = 仅封缸。
 */
public class ScheduleLockRequest {

    private Long id;
    private Long vatId;
    private Long cartId;
    private String startTime;
    private String endTime;
    private String lockReason;
    private String createdBy;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getVatId() {
        return vatId;
    }

    public void setVatId(Long vatId) {
        this.vatId = vatId;
    }

    public Long getCartId() {
        return cartId;
    }

    public void setCartId(Long cartId) {
        this.cartId = cartId;
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

    public String getLockReason() {
        return lockReason;
    }

    public void setLockReason(String lockReason) {
        this.lockReason = lockReason;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }
}
