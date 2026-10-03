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
import com.dyeing.mill.domain.ClothCart;
import com.dyeing.mill.dto.CartAssignRequest;
import com.dyeing.mill.dto.CartStatusRequest;
import com.dyeing.mill.dto.CustomerMeterDTO;
import com.dyeing.mill.service.CartService;

/**
 * 布车流转：看板三列、排缸、状态流转。
 */
@RestController
@RequestMapping("/api/carts")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping
    public ApiResponse<List<ClothCart>> list() {
        return ApiResponse.success(cartService.list());
    }

    @GetMapping("/board")
    public ApiResponse<Map<String, Object>> board() {
        return ApiResponse.success(cartService.board());
    }

    /** 排缸：落入「在染」列时选染缸 */
    @PostMapping("/{id}/assign")
    public ApiResponse<ClothCart> assign(@PathVariable Long id, @RequestBody CartAssignRequest request) {
        return ApiResponse.success("排缸成功", cartService.assign(id, request));
    }

    /** 拖拽改状态 */
    @PatchMapping("/{id}/status")
    public ApiResponse<ClothCart> changeStatus(@PathVariable Long id, @RequestBody CartStatusRequest request) {
        return ApiResponse.success("布车状态已更新", cartService.changeStatus(id, request));
    }

    /** 按客户汇总米数 */
    @GetMapping("/stats/customer")
    public ApiResponse<List<CustomerMeterDTO>> customerMeters() {
        return ApiResponse.success(cartService.customerMeters());
    }
}
