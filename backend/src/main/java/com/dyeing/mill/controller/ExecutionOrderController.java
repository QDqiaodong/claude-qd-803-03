package com.dyeing.mill.controller;

import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dyeing.mill.common.ApiResponse;
import com.dyeing.mill.domain.ExecutionOrder;
import com.dyeing.mill.dto.ExecutionOrderCreateRequest;
import com.dyeing.mill.dto.ExecutionOrderIssueRequest;
import com.dyeing.mill.service.ExecutionOrderService;

/**
 * 开缸执行单：预检、创建待确认单、下达并联动染缸参数。
 */
@RestController
@RequestMapping("/api/execution-orders")
public class ExecutionOrderController {

    private final ExecutionOrderService orderService;

    public ExecutionOrderController(ExecutionOrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping
    public ApiResponse<List<ExecutionOrder>> list() {
        return ApiResponse.success(orderService.list());
    }

    @GetMapping("/{id}")
    public ApiResponse<ExecutionOrder> detail(@PathVariable Long id) {
        return ApiResponse.success(orderService.detail(id));
    }

    @PostMapping("/preview")
    public ApiResponse<Map<String, Object>> preview(@Valid @RequestBody ExecutionOrderCreateRequest request) {
        return ApiResponse.success(orderService.preview(request));
    }

    @PostMapping
    public ApiResponse<ExecutionOrder> create(@Valid @RequestBody ExecutionOrderCreateRequest request) {
        return ApiResponse.success("待确认开缸执行单已创建", orderService.create(request));
    }

    @PostMapping("/{id}/issue")
    public ApiResponse<ExecutionOrder> issue(@PathVariable Long id,
                                             @Valid @RequestBody ExecutionOrderIssueRequest request) {
        return ApiResponse.success("开缸执行单已下达，染缸参数已同步", orderService.issue(id, request.getConfirmedBy()));
    }
}
