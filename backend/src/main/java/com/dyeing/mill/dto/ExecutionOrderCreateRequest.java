package com.dyeing.mill.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 创建开缸执行单：cartId 必填；配方和曲线可由后端按布种色号自动匹配。
 */
@Data
public class ExecutionOrderCreateRequest {

    @NotNull(message = "布车不能为空")
    private Long cartId;

    private Long recipeId;

    private Long curveId;

    private String createdBy;
}
