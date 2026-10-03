package com.dyeing.mill.controller;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dyeing.mill.common.ApiResponse;
import com.dyeing.mill.domain.DyeVat;
import com.dyeing.mill.dto.VatControlDTO;
import com.dyeing.mill.service.VatService;

/**
 * 染缸台账：缸位阵列数据、控温、阶段推进。
 */
@RestController
@RequestMapping("/api/vats")
public class VatController {

    private final VatService vatService;

    public VatController(VatService vatService) {
        this.vatService = vatService;
    }

    @GetMapping
    public ApiResponse<List<DyeVat>> list() {
        return ApiResponse.success(vatService.list());
    }

    @GetMapping("/overview")
    public ApiResponse<Map<String, Object>> overview() {
        return ApiResponse.success(vatService.overview());
    }

    @GetMapping("/stats/machine-type")
    public ApiResponse<List<Map<String, Object>>> machineTypeStats() {
        return ApiResponse.success(vatService.machineTypeStats());
    }

    @GetMapping("/{id}")
    public ApiResponse<DyeVat> detail(@PathVariable Long id) {
        return ApiResponse.success(vatService.detail(id));
    }

    /** 控温面板：设目标温度与保温时长 */
    @PatchMapping("/{id}/control")
    public ApiResponse<DyeVat> control(@PathVariable Long id, @RequestBody VatControlDTO dto) {
        return ApiResponse.success("控温指令已下达", vatService.control(id, dto));
    }

    /** 推进一拍：模拟温度自动变化 */
    @PostMapping("/{id}/tick")
    public ApiResponse<DyeVat> tick(@PathVariable Long id) {
        return ApiResponse.success(vatService.tick(id));
    }
}
