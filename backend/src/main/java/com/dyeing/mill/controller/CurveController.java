package com.dyeing.mill.controller;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dyeing.mill.common.ApiResponse;
import com.dyeing.mill.domain.FabricProfile;
import com.dyeing.mill.domain.ProcessCurve;
import com.dyeing.mill.dto.CurvePayload;
import com.dyeing.mill.service.CurveService;

/**
 * 工艺曲线：节点序列、升温速率计算与布种上限校验。
 */
@RestController
@RequestMapping("/api/curves")
public class CurveController {

    private final CurveService curveService;

    public CurveController(CurveService curveService) {
        this.curveService = curveService;
    }

    @GetMapping
    public ApiResponse<List<ProcessCurve>> list() {
        return ApiResponse.success(curveService.list());
    }

    @GetMapping("/fabrics")
    public ApiResponse<List<FabricProfile>> fabrics() {
        return ApiResponse.success(curveService.fabrics());
    }

    @GetMapping("/{id}")
    public ApiResponse<ProcessCurve> detail(@PathVariable Long id) {
        return ApiResponse.success(curveService.detail(id));
    }

    @PostMapping
    public ApiResponse<ProcessCurve> create(@RequestBody CurvePayload payload) {
        return ApiResponse.success("工艺曲线已创建", curveService.create(payload));
    }

    @PutMapping("/{id}/nodes")
    public ApiResponse<ProcessCurve> saveNodes(@PathVariable Long id, @RequestBody CurvePayload payload) {
        return ApiResponse.success("节点已保存", curveService.saveNodes(id, payload));
    }

    /** 只算不存：实时速率与超限提示 */
    @PostMapping("/{id}/validate")
    public ApiResponse<Map<String, Object>> validate(@PathVariable Long id, @RequestBody CurvePayload payload) {
        return ApiResponse.success(curveService.validate(id, payload));
    }
}
