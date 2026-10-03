package com.dyeing.mill.controller;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dyeing.mill.common.ApiResponse;
import com.dyeing.mill.domain.ChemicalStock;
import com.dyeing.mill.domain.IssueRecord;
import com.dyeing.mill.dto.IssueRequest;
import com.dyeing.mill.dto.StockAlertDTO;
import com.dyeing.mill.service.InventoryService;

/**
 * 染料与助剂库存：库存清单、预警、领用。
 */
@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping
    public ApiResponse<List<ChemicalStock>> list() {
        return ApiResponse.success(inventoryService.list());
    }

    @GetMapping("/overview")
    public ApiResponse<Map<String, Object>> overview() {
        return ApiResponse.success(inventoryService.overview());
    }

    /** 染料库存预警 */
    @GetMapping("/alerts")
    public ApiResponse<List<StockAlertDTO>> alerts() {
        return ApiResponse.success(inventoryService.alerts());
    }

    /** 领用 */
    @PostMapping("/issue")
    public ApiResponse<ChemicalStock> issue(@RequestBody IssueRequest request) {
        return ApiResponse.success("领用已登记", inventoryService.issue(request));
    }

    /** 领用记录 */
    @GetMapping("/issues")
    public ApiResponse<List<IssueRecord>> issues() {
        return ApiResponse.success(inventoryService.issues());
    }
}
