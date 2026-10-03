package com.dyeing.mill.dto;

import java.util.List;

/**
 * 发布新一版清洗规则：整矩阵提交（未提交的格视为清除），发布后新方案一律采用新口径。
 */
public class WashRulePublishRequest {

    private String changeNote;
    private String createdBy;
    private List<WashRuleCellInput> cells;

    public String getChangeNote() {
        return changeNote;
    }

    public void setChangeNote(String changeNote) {
        this.changeNote = changeNote;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public List<WashRuleCellInput> getCells() {
        return cells;
    }

    public void setCells(List<WashRuleCellInput> cells) {
        this.cells = cells;
    }
}
