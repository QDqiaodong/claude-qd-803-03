package com.dyeing.mill.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dyeing.mill.common.BizException;
import com.dyeing.mill.domain.ClothCart;
import com.dyeing.mill.domain.DyeVat;
import com.dyeing.mill.dto.CartAssignRequest;
import com.dyeing.mill.dto.CartStatusRequest;
import com.dyeing.mill.dto.CustomerMeterDTO;
import com.dyeing.mill.repository.ClothCartRepository;
import com.dyeing.mill.repository.DyeVatRepository;

/**
 * 布车流转业务：排缸（染缸时段冲突校验）、状态流转、客户米数汇总。
 */
@Service
public class CartService {

    private final ClothCartRepository cartRepository;
    private final DyeVatRepository vatRepository;
    private final ExecutionOrderService executionOrderService;

    public CartService(ClothCartRepository cartRepository,
                       DyeVatRepository vatRepository,
                       ExecutionOrderService executionOrderService) {
        this.cartRepository = cartRepository;
        this.vatRepository = vatRepository;
        this.executionOrderService = executionOrderService;
    }

    /** 报废布车已被 @SQLRestriction 全局过滤，这里拿到的都是有效布车 */
    public List<ClothCart> list() {
        return cartRepository.findAll();
    }

    public ClothCart detail(Long id) {
        return cartRepository.findById(id).orElseThrow(() -> new BizException("布车不存在或已报废：id=" + id));
    }

    /**
     * 排缸：把布车排到某个染缸的某个时段。
     * 规则：同一染缸同一时段只能有一个在制批次。
     */
    @Transactional
    public ClothCart assign(Long id, CartAssignRequest request) {
        ClothCart cart = cartRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BizException("布车不存在或已报废：id=" + id));
        Long vatId = request.getVatId();
        if (vatId == null) {
            throw new BizException("请先选择要排入的染缸");
        }
        DyeVat vat = vatRepository.findById(vatId)
                .orElseThrow(() -> new BizException("染缸不存在：id=" + vatId));

        if (executionOrderService.hasIssuedOrder(cart.getId())) {
            throw new BizException("布车 " + cart.getCartCode() + " 已有下达生效的开缸执行单，不能重新排缸");
        }

        LocalDateTime start = parseTime(request.getStartTime(), "开始时间");
        LocalDateTime end = parseTime(request.getEndTime(), "结束时间");
        if (!end.isAfter(start)) {
            throw new BizException("结束时间必须晚于开始时间");
        }

        List<ClothCart> overlaps = cartRepository.findOverlap(vatId, id, start, end);
        if (!overlaps.isEmpty()) {
            ClothCart conflict = overlaps.get(0);
            throw new BizException("染缸 " + vat.getVatCode() + " 在该时段已被布车 " + conflict.getCartCode()
                    + " 占用（" + conflict.getDyeStartTime() + " ~ " + conflict.getDyeEndTime()
                    + "），同一染缸同一时段只能有一个在制批次");
        }

        // 布种与浴比匹配提示：容量不够直接拦
        if (vat.getCapacityKg() != null && cart.getMeters() != null) {
            BigDecimal weight = cart.getMeters().multiply(BigDecimal.valueOf(0.18));
            if (weight.compareTo(vat.getCapacityKg()) > 0) {
                throw new BizException("布车 " + cart.getCartCode() + " 折算布重 " + weight.setScale(1, RoundingMode.HALF_UP)
                        + "kg 超过染缸 " + vat.getVatCode() + " 容量 " + vat.getCapacityKg() + "kg，请换大缸");
            }
        }

        Long oldVatPk = cart.getVatId();
        String oldVatCode = cart.getVatCode();
        LocalDateTime oldStart = cart.getDyeStartTime();
        LocalDateTime oldEnd = cart.getDyeEndTime();
        cart.setVatId(vat.getId());
        cart.setVatCode(vat.getVatCode());
        cart.setDyeStartTime(start);
        cart.setDyeEndTime(end);
        cart.setStatus("DYEING");
        ClothCart saved = cartRepository.save(cart);
        boolean cartAssignmentChanged = !java.util.Objects.equals(oldVatPk, vat.getId())
                || !java.util.Objects.equals(oldVatCode, vat.getVatCode())
                || !java.util.Objects.equals(oldStart, start)
                || !java.util.Objects.equals(oldEnd, end);
        if (cartAssignmentChanged) {
            executionOrderService.invalidateByCart(saved.getId(), "布车 " + saved.getCartCode()
                    + " 的排缸结果已变更为 " + oldVatCode + " → " + saved.getVatCode() + "（" + start + " ~ " + end + "）");
        }
        return saved;
    }

    /**
     * 拖拽看板改变布车状态。
     */
    @Transactional
    public ClothCart changeStatus(Long id, CartStatusRequest request) {
        ClothCart cart = cartRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BizException("布车不存在或已报废：id=" + id));
        String status = request.getStatus();
        if (status == null || status.isBlank()) {
            throw new BizException("目标状态不能为空");
        }
        String oldStatus = cart.getStatus();
        BigDecimal oldMeters = cart.getMeters();
        Long oldVatPk = cart.getVatId();
        String oldVatCode = cart.getVatCode();
        java.time.LocalDateTime oldStart = cart.getDyeStartTime();
        java.time.LocalDateTime oldEnd = cart.getDyeEndTime();
        Integer oldReworkTimes = cart.getReworkTimes();
        if ("DYEING".equals(status) && cart.getVatId() == null) {
            throw new BizException("布车 " + cart.getCartCode() + " 尚未排缸，请先选择染缸再转入在染");
        }
        if ("REWORK".equals(status)) {
            if (executionOrderService.hasIssuedOrder(cart.getId())) {
                throw new BizException("布车 " + cart.getCartCode() + " 已有下达生效的开缸执行单，不能直接转返修");
            }
            cart.setReworkTimes((cart.getReworkTimes() == null ? 0 : cart.getReworkTimes()) + 1);
            cart.setVatId(null);
            cart.setVatCode(null);
            cart.setDyeStartTime(null);
            cart.setDyeEndTime(null);
        }
        if (request.getMeters() != null) {
            cart.setMeters(request.getMeters());
        }
        if (request.getReworkTimes() != null) {
            cart.setReworkTimes(request.getReworkTimes());
        }
        cart.setStatus(status);
        ClothCart saved = cartRepository.save(cart);
        boolean sourceChanged = !java.util.Objects.equals(oldStatus, saved.getStatus())
                || !java.util.Objects.equals(oldMeters, saved.getMeters())
                || !java.util.Objects.equals(oldVatPk, saved.getVatId())
                || !java.util.Objects.equals(oldVatCode, saved.getVatCode())
                || !java.util.Objects.equals(oldStart, saved.getDyeStartTime())
                || !java.util.Objects.equals(oldEnd, saved.getDyeEndTime())
                || !java.util.Objects.equals(oldReworkTimes, saved.getReworkTimes());
        if (sourceChanged) {
            executionOrderService.invalidateByCart(saved.getId(), "布车 " + saved.getCartCode()
                    + " 的状态或排缸结果已改变为 " + saved.getStatus());
        }
        return saved;
    }

    /**
     * 按客户汇总米数。
     */
    public List<CustomerMeterDTO> customerMeters() {
        return cartRepository.statMetersByCustomer();
    }

    /**
     * 看板数据：三列（待排产 / 在染 / 已完成），返修归入待排产列并带标记。
     */
    public Map<String, Object> board() {
        Map<String, List<ClothCart>> columns = new LinkedHashMap<>();
        columns.put("PENDING", new ArrayList<>());
        columns.put("DYEING", new ArrayList<>());
        columns.put("DONE", new ArrayList<>());
        int reworkCount = 0;
        for (ClothCart cart : cartRepository.findAll()) {
            String status = cart.getStatus() == null ? "PENDING" : cart.getStatus();
            if ("REWORK".equals(status)) {
                reworkCount++;
                columns.get("PENDING").add(cart);
            } else if (columns.containsKey(status)) {
                columns.get(status).add(cart);
            }
        }
        Map<String, Object> data = new HashMap<>();
        data.put("columns", columns);
        data.put("reworkCount", reworkCount);
        data.put("totalMeters", cartRepository.findAll().stream()
                .map(c -> c.getMeters() == null ? BigDecimal.ZERO : c.getMeters())
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        return data;
    }

    private LocalDateTime parseTime(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new BizException(label + "不能为空");
        }
        try {
            return LocalDateTime.parse(value.replace(" ", "T"));
        } catch (DateTimeParseException e) {
            throw new BizException(label + "格式有误，应为 yyyy-MM-dd HH:mm");
        }
    }
}
