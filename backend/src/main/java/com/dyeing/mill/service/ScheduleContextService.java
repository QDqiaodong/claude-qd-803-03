package com.dyeing.mill.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dyeing.mill.domain.ClothCart;
import com.dyeing.mill.domain.ColorFamily;
import com.dyeing.mill.domain.ColorFamilyMap;
import com.dyeing.mill.domain.DyeVat;
import com.dyeing.mill.domain.FabricMachine;
import com.dyeing.mill.domain.MaintenanceWindow;
import com.dyeing.mill.domain.ProcessCurve;
import com.dyeing.mill.domain.Recipe;
import com.dyeing.mill.domain.ScheduleLock;
import com.dyeing.mill.repository.ClothCartRepository;
import com.dyeing.mill.repository.ColorFamilyMapRepository;
import com.dyeing.mill.repository.ColorFamilyRepository;
import com.dyeing.mill.repository.DyeVatRepository;
import com.dyeing.mill.repository.FabricMachineRepository;
import com.dyeing.mill.repository.MaintenanceWindowRepository;
import com.dyeing.mill.repository.ProcessCurveRepository;
import com.dyeing.mill.repository.RecipeRepository;
import com.dyeing.mill.repository.ScheduleLockRepository;

/**
 * 排程台上下文：待排布车池（含交期/急单/色族/配方就绪情况）、染缸台账、
 * 维护与锁定段、色族与适配表、当前规则版本 —— 页面首屏与解释面板共用。
 */
@Service
public class ScheduleContextService {

    private final ClothCartRepository cartRepository;
    private final DyeVatRepository vatRepository;
    private final ColorFamilyRepository familyRepository;
    private final ColorFamilyMapRepository familyMapRepository;
    private final FabricMachineRepository fabricMachineRepository;
    private final MaintenanceWindowRepository maintenanceRepository;
    private final ScheduleLockRepository lockRepository;
    private final RecipeRepository recipeRepository;
    private final ProcessCurveRepository curveRepository;
    private final WashRuleService washRuleService;

    public ScheduleContextService(ClothCartRepository cartRepository,
                                  DyeVatRepository vatRepository,
                                  ColorFamilyRepository familyRepository,
                                  ColorFamilyMapRepository familyMapRepository,
                                  FabricMachineRepository fabricMachineRepository,
                                  MaintenanceWindowRepository maintenanceRepository,
                                  ScheduleLockRepository lockRepository,
                                  RecipeRepository recipeRepository,
                                  ProcessCurveRepository curveRepository,
                                  WashRuleService washRuleService) {
        this.cartRepository = cartRepository;
        this.vatRepository = vatRepository;
        this.familyRepository = familyRepository;
        this.familyMapRepository = familyMapRepository;
        this.fabricMachineRepository = fabricMachineRepository;
        this.maintenanceRepository = maintenanceRepository;
        this.lockRepository = lockRepository;
        this.recipeRepository = recipeRepository;
        this.curveRepository = curveRepository;
        this.washRuleService = washRuleService;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> context() {
        Map<String, Object> data = new LinkedHashMap<>();
        List<ClothCart> carts = cartRepository.findAll();
        List<DyeVat> vats = vatRepository.findAll();
        List<ColorFamily> families = familyRepository.findAllOrdered();
        List<ColorFamilyMap> maps = familyMapRepository.findAll();
        List<FabricMachine> fms = fabricMachineRepository.findAll();
        List<MaintenanceWindow> maintenance = maintenanceRepository.findAllOrdered();
        List<ScheduleLock> locks = lockRepository.findAllOrdered();
        List<Recipe> recipes = recipeRepository.findAll();
        List<ProcessCurve> curves = curveRepository.findAll();

        java.util.Set<String> releasedKeys = recipes.stream()
                .filter(r -> "RELEASED".equals(r.getStatus()))
                .map(r -> r.getFabricType() + "|" + r.getColorCode())
                .collect(java.util.stream.Collectors.toSet());
        java.util.Set<String> curveKeys = curves.stream()
                .map(c -> c.getFabricType() + "|" + c.getColorCode())
                .collect(java.util.stream.Collectors.toSet());

        Map<String, Long> colorToFamily = new LinkedHashMap<>();
        for (ColorFamilyMap m : maps) {
            colorToFamily.put(m.getColorCode(), m.getFamilyId());
        }
        Map<Long, ColorFamily> familyById = new LinkedHashMap<>();
        families.forEach(f -> familyById.put(f.getId(), f));

        List<Map<String, Object>> pending = carts.stream()
                .filter(c -> "PENDING".equals(c.getStatus()) || "REWORK".equals(c.getStatus()))
                .sorted((a, b) -> {
                    int rush = Boolean.compare(b.getRushFlag() != null && b.getRushFlag() == 1,
                            a.getRushFlag() != null && a.getRushFlag() == 1);
                    if (rush != 0) {
                        return rush;
                    }
                    if (a.getDueTime() == null && b.getDueTime() == null) {
                        return a.getId().compareTo(b.getId());
                    }
                    if (a.getDueTime() == null) {
                        return 1;
                    }
                    if (b.getDueTime() == null) {
                        return -1;
                    }
                    return a.getDueTime().compareTo(b.getDueTime());
                })
                .map(c -> cartCard(c, colorToFamily, familyById, fms, vats, releasedKeys, curveKeys))
                .toList();

        data.put("pendingCarts", pending);
        data.put("carts", carts);
        data.put("vats", vats);
        data.put("families", families);
        data.put("colorFamilyMap", colorToFamily);
        data.put("fabricMachines", fms);
        data.put("maintenance", maintenance);
        data.put("locks", locks);
        data.put("activeRuleVersion", washRuleService.versionView(washRuleService.activeVersion()));
        return data;
    }

    private Map<String, Object> cartCard(ClothCart c, Map<String, Long> colorToFamily,
                                         Map<Long, ColorFamily> familyById,
                                         List<FabricMachine> fms, List<DyeVat> vats,
                                         java.util.Set<String> releasedKeys,
                                         java.util.Set<String> curveKeys) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId());
        m.put("cartCode", c.getCartCode());
        m.put("fabricType", c.getFabricType());
        m.put("colorCode", c.getColorCode());
        m.put("colorName", c.getColorName());
        m.put("customer", c.getCustomer());
        m.put("meters", c.getMeters());
        m.put("weightKg", c.getMeters() == null ? null : c.getMeters().multiply(ScheduleInputAssembler.METERS_TO_KG));
        m.put("status", c.getStatus());
        m.put("dueTime", c.getDueTime());
        m.put("rushFlag", c.getRushFlag());
        m.put("reworkTimes", c.getReworkTimes());
        Long familyId = colorToFamily.get(c.getColorCode());
        ColorFamily family = familyId == null ? null : familyById.get(familyId);
        m.put("familyId", familyId);
        m.put("familyCode", family == null ? null : family.getFamilyCode());
        m.put("familyName", family == null ? null : family.getFamilyName());

        // 排前预检：把缺资料原因直接算在卡片上，方便调度员一眼看到
        java.util.List<String> issues = new java.util.ArrayList<>();
        if (familyId == null) {
            issues.add("色号未配置颜色族");
        }
        if (!releasedKeys.contains(c.getFabricType() + "|" + c.getColorCode())) {
            issues.add("没有已发布配方");
        }
        if (!curveKeys.contains(c.getFabricType() + "|" + c.getColorCode())) {
            issues.add("无匹配工艺曲线（将按布种默认时长估算）");
        }
        List<String> machines = fms.stream()
                .filter(f -> f.getFabricType().equals(c.getFabricType()))
                .map(FabricMachine::getMachineType).distinct().toList();
        if (machines.isEmpty()) {
            issues.add("布种未配置适配机型");
        }
        if (c.getMeters() != null) {
            java.math.BigDecimal weight = c.getMeters().multiply(ScheduleInputAssembler.METERS_TO_KG);
            boolean fit = vats.stream().anyMatch(v -> machines.contains(v.getMachineType())
                    && v.getCapacityKg() != null && v.getCapacityKg().compareTo(weight) >= 0);
            if (!machines.isEmpty() && !fit) {
                issues.add("所有适配缸容量都不足");
            }
        }
        m.put("issues", issues);
        return m;
    }
}
