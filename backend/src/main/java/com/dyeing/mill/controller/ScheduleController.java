package com.dyeing.mill.controller;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.dyeing.mill.common.ApiResponse;
import com.dyeing.mill.domain.ClothCart;
import com.dyeing.mill.domain.ColorFamily;
import com.dyeing.mill.domain.MaintenanceWindow;
import com.dyeing.mill.domain.ScheduleLock;
import com.dyeing.mill.domain.SchedulePlan;
import com.dyeing.mill.domain.WashRuleVersion;
import com.dyeing.mill.dto.CartSchedulePatchRequest;
import com.dyeing.mill.dto.MaintenanceWindowRequest;
import com.dyeing.mill.dto.ScheduleGenerateRequest;
import com.dyeing.mill.dto.ScheduleLockRequest;
import com.dyeing.mill.dto.WashRulePublishRequest;
import com.dyeing.mill.service.ColorFamilyLookup;
import com.dyeing.mill.service.ScheduleContextService;
import com.dyeing.mill.service.ScheduleMasterDataService;
import com.dyeing.mill.service.SchedulePlanService;
import com.dyeing.mill.service.WashRuleService;

/**
 * 染缸换色排程台：
 * 上下文数据、交期/急单维护、维护时段、人工锁定段、清洗矩阵版本、方案生成（异步作业）/留存/复算。
 * 所有接口只读写排程域，绝不回写布车状态或创建开缸执行单。
 */
@RestController
@RequestMapping("/api/schedule")
public class ScheduleController {

    private final ScheduleContextService contextService;
    private final ScheduleMasterDataService masterDataService;
    private final SchedulePlanService planService;
    private final WashRuleService washRuleService;
    private final ColorFamilyLookup familyLookup;

    public ScheduleController(ScheduleContextService contextService,
                              ScheduleMasterDataService masterDataService,
                              SchedulePlanService planService,
                              WashRuleService washRuleService,
                              ColorFamilyLookup familyLookup) {
        this.contextService = contextService;
        this.masterDataService = masterDataService;
        this.planService = planService;
        this.washRuleService = washRuleService;
        this.familyLookup = familyLookup;
    }

    // ---------------- 首屏上下文 ----------------

    @GetMapping("/context")
    public ApiResponse<Map<String, Object>> context() {
        return ApiResponse.success(contextService.context());
    }

    // ---------------- 布车交期 / 急单（仅排程字段） ----------------

    @PatchMapping("/carts/{id}")
    public ApiResponse<ClothCart> patchCart(@PathVariable Long id,
                                            @RequestBody CartSchedulePatchRequest request) {
        return ApiResponse.success("交期/急单已更新（不影响布车状态与排缸结果）",
                masterDataService.patchCartSchedule(id, request));
    }

    // ---------------- 维护时段 ----------------

    @GetMapping("/maintenance")
    public ApiResponse<List<MaintenanceWindow>> listMaintenance() {
        return ApiResponse.success(masterDataService.listMaintenance());
    }

    @PostMapping("/maintenance")
    public ApiResponse<MaintenanceWindow> createMaintenance(@RequestBody MaintenanceWindowRequest request) {
        return ApiResponse.success("维护时段已登记", masterDataService.createMaintenance(request));
    }

    @DeleteMapping("/maintenance/{id}")
    public ApiResponse<Void> deleteMaintenance(@PathVariable Long id) {
        masterDataService.deleteMaintenance(id);
        return ApiResponse.success("维护时段已删除", null);
    }

    // ---------------- 人工锁定段 ----------------

    @GetMapping("/locks")
    public ApiResponse<List<ScheduleLock>> listLocks() {
        return ApiResponse.success(masterDataService.listLocks());
    }

    @PostMapping("/locks")
    public ApiResponse<ScheduleLock> createLock(@RequestBody ScheduleLockRequest request) {
        return ApiResponse.success("承诺缸位已锁定，重算将保留该段", masterDataService.createLock(request));
    }

    @DeleteMapping("/locks/{id}")
    public ApiResponse<Void> deleteLock(@PathVariable Long id) {
        masterDataService.deleteLock(id);
        return ApiResponse.success("锁定段已解除", null);
    }

    // ---------------- 颜色族 ----------------

    @GetMapping("/families")
    public ApiResponse<List<ColorFamily>> families() {
        return ApiResponse.success(familyLookup.all());
    }

    // ---------------- 清洗规则版本 / 矩阵 ----------------

    @GetMapping("/wash-versions")
    public ApiResponse<List<WashRuleVersion>> washVersions() {
        return ApiResponse.success(washRuleService.listVersions());
    }

    @GetMapping("/wash-matrix")
    public ApiResponse<Map<String, Object>> washMatrix(
            @RequestParam(value = "versionId", required = false) Long versionId) {
        return ApiResponse.success(washRuleService.matrixView(versionId));
    }

    @PostMapping("/wash-versions")
    public ApiResponse<WashRuleVersion> publishWashVersion(@RequestBody WashRulePublishRequest request) {
        WashRuleVersion v = washRuleService.publish(request);
        return ApiResponse.success("清洗规则 V" + v.getVersionNo() + " 已发布，之后的新方案按新口径计算", v);
    }

    // ---------------- 方案：生成（异步）/ 查询 / 留存 / 复算 ----------------

    @PostMapping("/plans/generate")
    public ApiResponse<SchedulePlanService.JobSubmit> generate(@RequestBody ScheduleGenerateRequest request) {
        SchedulePlanService.JobSubmit submit = planService.submitGenerate(request);
        String msg = submit.merged ? "已有相同输入的重算在进行，已合并到该作业" : "重算已开始（后台执行，不阻塞页面）";
        return ApiResponse.success(msg, submit);
    }

    @GetMapping("/jobs/{jobId}")
    public ApiResponse<SchedulePlanService.Job> job(@PathVariable long jobId) {
        SchedulePlanService.Job job = planService.getJob(jobId);
        if (job == null) {
            return ApiResponse.success(null);
        }
        return ApiResponse.success(job);
    }

    @GetMapping("/plans")
    public ApiResponse<List<SchedulePlan>> plans() {
        return ApiResponse.success(planService.listPlans());
    }

    @GetMapping("/plans/{id}")
    public ApiResponse<Map<String, Object>> plan(@PathVariable Long id) {
        return ApiResponse.success(planService.planView(id));
    }

    @PostMapping("/plans/{id}/reference")
    public ApiResponse<SchedulePlan> markReference(@PathVariable Long id) {
        return ApiResponse.success("已选定为车间参考方案（仅标注，不下发布车/开缸）", planService.markReference(id));
    }

    @PatchMapping("/plans/{id}/name")
    public ApiResponse<SchedulePlan> rename(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return ApiResponse.success("方案已留存改名", planService.rename(id, body.get("planName")));
    }

    @PostMapping("/plans/{id}/replay")
    public ApiResponse<SchedulePlan> replay(@PathVariable Long id,
                                            @RequestBody(required = false) Map<String, String> body) {
        String createdBy = body == null ? null : body.get("createdBy");
        return ApiResponse.success("已按方案当时的规则版本复算（新方案为草稿）", planService.replay(id, createdBy));
    }
}
