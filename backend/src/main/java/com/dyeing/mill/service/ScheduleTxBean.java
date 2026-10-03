package com.dyeing.mill.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Component;
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
import com.dyeing.mill.domain.SchedulePlan;
import com.dyeing.mill.domain.SchedulePlanAssignment;
import com.dyeing.mill.domain.WashRuleVersion;
import com.dyeing.mill.engine.ScheduleInput;
import com.dyeing.mill.engine.ScheduleOutput;
import com.dyeing.mill.repository.SchedulePlanAssignmentRepository;
import com.dyeing.mill.repository.SchedulePlanRepository;

/**
 * 排程事务边界：后台重算线程没有请求事务，统一经此 Bean 进入只读取数事务与写入事务，
 * 避免同类自调用导致 @Transactional 不生效。
 */
@Component
public class ScheduleTxBean {

    private final WashRuleService washRuleService;
    private final ScheduleInputAssembler assembler;
    private final com.dyeing.mill.repository.ClothCartRepository cartRepository;
    private final com.dyeing.mill.repository.DyeVatRepository vatRepository;
    private final com.dyeing.mill.repository.ColorFamilyRepository familyRepository;
    private final com.dyeing.mill.repository.ColorFamilyMapRepository familyMapRepository;
    private final com.dyeing.mill.repository.RecipeRepository recipeRepository;
    private final com.dyeing.mill.repository.ProcessCurveRepository curveRepository;
    private final com.dyeing.mill.repository.FabricMachineRepository fabricMachineRepository;
    private final com.dyeing.mill.repository.MaintenanceWindowRepository maintenanceRepository;
    private final com.dyeing.mill.repository.ScheduleLockRepository lockRepository;
    private final SchedulePlanRepository planRepository;
    private final SchedulePlanAssignmentRepository assignmentRepository;
    private final ScheduleSnapshots snapshots;

    public ScheduleTxBean(WashRuleService washRuleService,
                          ScheduleInputAssembler assembler,
                          com.dyeing.mill.repository.ClothCartRepository cartRepository,
                          com.dyeing.mill.repository.DyeVatRepository vatRepository,
                          com.dyeing.mill.repository.ColorFamilyRepository familyRepository,
                          com.dyeing.mill.repository.ColorFamilyMapRepository familyMapRepository,
                          com.dyeing.mill.repository.RecipeRepository recipeRepository,
                          com.dyeing.mill.repository.ProcessCurveRepository curveRepository,
                          com.dyeing.mill.repository.FabricMachineRepository fabricMachineRepository,
                          com.dyeing.mill.repository.MaintenanceWindowRepository maintenanceRepository,
                          com.dyeing.mill.repository.ScheduleLockRepository lockRepository,
                          SchedulePlanRepository planRepository,
                          SchedulePlanAssignmentRepository assignmentRepository,
                          ScheduleSnapshots snapshots) {
        this.washRuleService = washRuleService;
        this.assembler = assembler;
        this.cartRepository = cartRepository;
        this.vatRepository = vatRepository;
        this.familyRepository = familyRepository;
        this.familyMapRepository = familyMapRepository;
        this.recipeRepository = recipeRepository;
        this.curveRepository = curveRepository;
        this.fabricMachineRepository = fabricMachineRepository;
        this.maintenanceRepository = maintenanceRepository;
        this.lockRepository = lockRepository;
        this.planRepository = planRepository;
        this.assignmentRepository = assignmentRepository;
        this.snapshots = snapshots;
    }

    @Transactional(readOnly = true)
    public SchedulePlanService.GenerateBundle loadLiveBundle(int horizonHours, LocalDateTime anchor) {
        LocalDateTime horizonEnd = anchor.plusHours(horizonHours);
        WashRuleVersion rule = washRuleService.activeVersion();
        List<ClothCart> carts = cartRepository.findAll();
        List<DyeVat> vats = vatRepository.findAll();
        List<ColorFamily> families = familyRepository.findAllOrdered();
        List<ColorFamilyMap> maps = familyMapRepository.findAll();
        List<Recipe> recipes = recipeRepository.findAll();
        List<ProcessCurve> curves = curveRepository.findAll();
        List<FabricMachine> fms = fabricMachineRepository.findAll();
        List<MaintenanceWindow> maintenance = maintenanceRepository.findAllOrdered();
        List<ScheduleLock> locks = lockRepository.findAllOrdered();

        ScheduleInput input = assembler.fromLiveData(anchor, horizonEnd, rule, carts, vats, families, maps,
                recipes, curves, fms, maintenance, locks);
        SchedulePlanService.GenerateBundle bundle = new SchedulePlanService.GenerateBundle();
        bundle.input = input;
        bundle.rule = rule;
        return bundle;
    }

    /**
     * 方案头 + 明细同一事务落库（后台重算线程无外层事务，必须经此代理 Bean 进入事务）。
     */
    @Transactional
    public SchedulePlan persistPlan(String planName, String createdBy,
                                    SchedulePlanService.GenerateBundle bundle, ScheduleOutput output, String mode) {
        SchedulePlan plan = new SchedulePlan();
        plan.setPlanCode(generatePlanCode());
        plan.setPlanName(planName == null || planName.isBlank()
                ? "排程方案 " + LocalDateTime.now().toLocalDate() : planName.trim());
        plan.setStatus("DRAFT");
        plan.setRuleVersionId(bundle.rule.getId());
        plan.setRuleVersionNo(bundle.rule.getVersionNo());
        plan.setHorizonStartTime(bundle.input.getAnchor());
        plan.setHorizonEndTime(bundle.input.getHorizonEnd());
        plan.setAnchorMode(mode);
        plan.setInputSnapshotJson(snapshots.write(assembler.toSnapshotMap(bundle.input)));

        int total = (int) output.getResults().stream()
                .filter(r -> !"FIXED".equals(r.getResultType())).count();
        plan.setTotalCarts(total);
        plan.setScheduledCarts(output.getScheduledCount());
        plan.setExcludedCarts(output.getExcludedCount());
        plan.setLateCarts(output.getLateCount());
        plan.setTotalWashMinutes(output.getTotalWashMinutes());
        plan.setTotalWashCost(output.getTotalWashCost());
        plan.setTotalIdleMinutes(output.getTotalIdleMinutes());
        plan.setTotalLateMinutes(output.getTotalLateMinutes());
        plan.setCreatedBy(normalize(createdBy, "调度员"));
        plan.setCreateTime(LocalDateTime.now());
        SchedulePlan saved = planRepository.save(plan);

        List<SchedulePlanAssignment> rows = new java.util.ArrayList<>();
        for (ScheduleOutput.EResult r : output.getResults()) {
            rows.add(toAssignment(saved.getId(), r));
        }
        assignmentRepository.saveAll(rows);
        saved.getAssignments().addAll(rows);
        return saved;
    }

    private SchedulePlanAssignment toAssignment(Long planId, ScheduleOutput.EResult r) {
        SchedulePlanAssignment a = new SchedulePlanAssignment();
        a.setPlanId(planId);
        a.setCartId(r.getCartId());
        a.setCartCode(r.getCartCode());
        a.setResultType(r.getResultType());
        a.setVatId(r.getVatId());
        a.setVatCode(r.getVatCode());
        a.setStartTime(r.getStart());
        a.setEndTime(r.getEnd());
        a.setWashBeforeMin(r.getWashBeforeMin());
        a.setWashBeforeCost(r.getWashBeforeCost());
        a.setWashFromCartId(r.getWashFromCartId());
        a.setSwitchType(r.getSwitchType());
        a.setFamilyCode(r.getFamilyCode());
        a.setLateMinutes(r.getLateMinutes());
        a.setLocked(r.isLocked() ? 1 : 0);
        String reason = r.getReason() == null ? "" : r.getReason();
        a.setReasonText(reason.length() > 500 ? reason.substring(0, 500) : reason);
        return a;
    }

    private String generatePlanCode() {
        LocalDateTime now = LocalDateTime.now();
        String suffix = String.format("%06d", Math.abs(System.nanoTime()) % 1_000_000);
        return "PL-" + now.getYear() + String.format("%02d", now.getMonthValue())
                + String.format("%02d", now.getDayOfMonth()) + "-" + suffix;
    }

    private String normalize(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String t = value.trim();
        return t.length() > 32 ? t.substring(0, 32) : t;
    }
}
