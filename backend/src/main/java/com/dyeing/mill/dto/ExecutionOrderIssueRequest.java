package com.dyeing.mill.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 下达开缸执行单。
 */
@Data
public class ExecutionOrderIssueRequest {

    @NotBlank(message = "下达人员不能为空")
    private String confirmedBy;
}
