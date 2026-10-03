package com.dyeing.mill.controller;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dyeing.mill.common.ApiResponse;
import com.dyeing.mill.domain.Recipe;
import com.dyeing.mill.dto.ColorStatDTO;
import com.dyeing.mill.dto.RecipePayload;
import com.dyeing.mill.service.RecipeService;

/**
 * 染色配方：色卡、染料阶梯表、成本核算、版本演进。
 */
@RestController
@RequestMapping("/api/recipes")
public class RecipeController {

    private final RecipeService recipeService;

    public RecipeController(RecipeService recipeService) {
        this.recipeService = recipeService;
    }

    @GetMapping
    public ApiResponse<List<Recipe>> list() {
        return ApiResponse.success(recipeService.list());
    }

    @GetMapping("/{id}")
    public ApiResponse<Recipe> detail(@PathVariable Long id) {
        return ApiResponse.success(recipeService.detail(id));
    }

    @PostMapping
    public ApiResponse<Recipe> create(@RequestBody RecipePayload payload) {
        return ApiResponse.success("配方已创建", recipeService.create(payload));
    }

    @PutMapping("/{id}")
    public ApiResponse<Recipe> update(@PathVariable Long id, @RequestBody RecipePayload payload) {
        return ApiResponse.success("配方已保存", recipeService.update(id, payload));
    }

    @PostMapping("/{id}/clone")
    public ApiResponse<Recipe> cloneVersion(@PathVariable Long id) {
        return ApiResponse.success("已生成新版本配方", recipeService.cloneVersion(id));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<String> delete(@PathVariable Long id) {
        recipeService.delete(id);
        return ApiResponse.success("配方已删除", "OK");
    }

    /** 按色号统计缸次 */
    @GetMapping("/stats/color")
    public ApiResponse<List<ColorStatDTO>> colorStats() {
        return ApiResponse.success(recipeService.colorStats());
    }

    /** 按客户统计配方数与平均成本 */
    @GetMapping("/stats/customer")
    public ApiResponse<List<Map<String, Object>>> customerStats() {
        return ApiResponse.success(recipeService.customerStats());
    }
}
