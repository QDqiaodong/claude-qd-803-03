package com.dyeing.mill.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dyeing.mill.common.BizException;
import com.dyeing.mill.domain.DyeVat;
import com.dyeing.mill.dto.VatControlDTO;
import com.dyeing.mill.repository.DyeVatRepository;

/**
 * 染缸台账业务：控温、阶段推进、机台类型统计。
 */
@Service
public class VatService {

    /** 机台类型 → 允许的最高温度（℃） */
    private static final Map<String, BigDecimal> MACHINE_MAX_TEMP = Map.of(
            "OVERFLOW", BigDecimal.valueOf(135),
            "AIRFLOW", BigDecimal.valueOf(140),
            "JIG", BigDecimal.valueOf(98));

    private static final BigDecimal AMBIENT_TEMP = BigDecimal.valueOf(32);

    private final DyeVatRepository vatRepository;
    private final StringRedisTemplate redisTemplate;

    public VatService(DyeVatRepository vatRepository, StringRedisTemplate redisTemplate) {
        this.vatRepository = vatRepository;
        this.redisTemplate = redisTemplate;
    }

    public List<DyeVat> list() {
        return vatRepository.findAll();
    }

    public DyeVat detail(Long id) {
        return vatRepository.findById(id).orElseThrow(() -> new BizException("染缸不存在或已停用：id=" + id));
    }

    /**
     * 控温面板：设定目标温度与保温时长，并按温差点自动进入升温/保温/降温阶段。
     */
    @Transactional
    public DyeVat control(Long id, VatControlDTO dto) {
        DyeVat vat = detail(id);
        BigDecimal target = dto.getTargetTemp();
        if (target == null) {
            throw new BizException("请填写目标温度");
        }
        Integer holdMinutes = dto.getHoldMinutes() == null ? 0 : dto.getHoldMinutes();
        if (holdMinutes < 0 || holdMinutes > 240) {
            throw new BizException("保温时长需在 0 ~ 240 分钟之间");
        }

        BigDecimal limit = MACHINE_MAX_TEMP.get(vat.getMachineType());
        if (limit != null && target.compareTo(limit) > 0) {
            throw new BizException("目标温度 " + target + "℃ 超过" + vat.getVatCode() + "机台上限 " + limit + "℃，无法下达");
        }

        BigDecimal current = vat.getCurrentTemp() == null ? AMBIENT_TEMP : vat.getCurrentTemp();
        vat.setTargetTemp(target);
        vat.setHoldMinutes(holdMinutes);

        int cmp = target.compareTo(current);
        if (cmp > 0) {
            vat.setStatus("HEATING");
            vat.setHoldRemainMinutes(holdMinutes);
        } else if (cmp < 0) {
            vat.setStatus("COOLING");
            vat.setHoldRemainMinutes(0);
        } else {
            vat.setStatus("HOLDING");
            vat.setHoldRemainMinutes(holdMinutes);
        }

        DyeVat saved = vatRepository.save(vat);
        redisTemplate.opsForValue().set("mill:vat:" + saved.getVatCode() + ":target", target.toPlainString());
        return saved;
    }

    /**
     * 推进一个节拍：模拟温度按阶段自动变化，方便演示缸位环的实时跳动。
     */
    @Transactional
    public DyeVat tick(Long id) {
        DyeVat vat = detail(id);
        BigDecimal current = vat.getCurrentTemp() == null ? AMBIENT_TEMP : vat.getCurrentTemp();
        BigDecimal target = vat.getTargetTemp() == null ? current : vat.getTargetTemp();

        switch (vat.getStatus() == null ? "IDLE" : vat.getStatus()) {
            case "HEATING" -> {
                current = current.add(BigDecimal.valueOf(5)).min(target);
                vat.setCurrentTemp(current.setScale(1, RoundingMode.HALF_UP));
                if (current.compareTo(target) >= 0) {
                    vat.setStatus("HOLDING");
                    vat.setHoldRemainMinutes(vat.getHoldMinutes());
                }
            }
            case "HOLDING" -> {
                int remain = vat.getHoldRemainMinutes() == null ? 0 : vat.getHoldRemainMinutes() - 5;
                vat.setHoldRemainMinutes(Math.max(remain, 0));
                if (remain <= 0) {
                    vat.setStatus("COOLING");
                    vat.setTargetTemp(BigDecimal.valueOf(45));
                }
            }
            case "COOLING" -> {
                current = current.subtract(BigDecimal.valueOf(6)).max(BigDecimal.valueOf(45));
                vat.setCurrentTemp(current.setScale(1, RoundingMode.HALF_UP));
                if (current.compareTo(BigDecimal.valueOf(45)) <= 0) {
                    vat.setStatus("DRAINING");
                }
            }
            case "DRAINING" -> vat.setStatus("WASHING");
            case "WASHING" -> {
                vat.setStatus("IDLE");
                vat.setCurrentTemp(AMBIENT_TEMP);
                vat.setTargetTemp(AMBIENT_TEMP);
                vat.setHoldRemainMinutes(0);
            }
            default -> {
                if (target.compareTo(current) > 0) {
                    vat.setStatus("HEATING");
                } else {
                    throw new BizException(vat.getVatCode() + " 当前空闲，请先在控温面板设定目标温度");
                }
            }
        }
        return vatRepository.save(vat);
    }

    /**
     * 机台类型分布统计。
     */
    public List<Map<String, Object>> machineTypeStats() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object[] row : vatRepository.statByMachineType()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("machineType", row[0]);
            item.put("vatCount", row[1]);
            item.put("totalCapacityKg", row[2]);
            result.add(item);
        }
        return result;
    }

    /**
     * 缸位总览：各状态缸数。
     */
    public Map<String, Object> overview() {
        Map<String, Long> statusCount = new LinkedHashMap<>();
        for (DyeVat vat : vatRepository.findAll()) {
            String status = vat.getStatus() == null ? "IDLE" : vat.getStatus();
            statusCount.merge(status, 1L, Long::sum);
        }
        Map<String, Object> data = new HashMap<>();
        data.put("total", vatRepository.count());
        data.put("running", vatRepository.findRunningVats().size());
        data.put("statusCount", statusCount);
        return data;
    }
}
