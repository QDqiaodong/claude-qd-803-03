package com.dyeing.mill.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dyeing.mill.common.BizException;
import com.dyeing.mill.domain.CurveNode;
import com.dyeing.mill.domain.FabricProfile;
import com.dyeing.mill.domain.ProcessCurve;
import com.dyeing.mill.dto.CurveNodeInput;
import com.dyeing.mill.dto.CurvePayload;
import com.dyeing.mill.repository.FabricProfileRepository;
import com.dyeing.mill.repository.ProcessCurveRepository;

/**
 * 工艺曲线业务：节点序列有序保存、各段升温速率计算与布种上限拦截。
 *
 * <p>预检（validate）与保存（create/saveNodes）共用同一套草稿校验 {@link #checkDraft}：
 * 草稿的布种、起始温度与节点序列以同一口径进入两条链路，规则只维护一处。
 * 预检纯只读，绝不落库；保存时规则不通过则在写入前抛出，编辑器内容原样保留。
 */
@Service
public class CurveService {

    private static final BigDecimal DEFAULT_START_TEMP = BigDecimal.valueOf(30);

    private final ProcessCurveRepository curveRepository;
    private final FabricProfileRepository fabricProfileRepository;
    private final ExecutionOrderService executionOrderService;

    public CurveService(ProcessCurveRepository curveRepository,
                        FabricProfileRepository fabricProfileRepository,
                        ExecutionOrderService executionOrderService) {
        this.curveRepository = curveRepository;
        this.fabricProfileRepository = fabricProfileRepository;
        this.executionOrderService = executionOrderService;
    }

    public List<ProcessCurve> list() {
        return curveRepository.findAll();
    }

    public ProcessCurve detail(Long id) {
        return curveRepository.findById(id).orElseThrow(() -> new BizException("工艺曲线不存在：id=" + id));
    }

    public List<FabricProfile> fabrics() {
        return fabricProfileRepository.findAllOrderByRampRate();
    }

    @Transactional
    public ProcessCurve create(CurvePayload payload) {
        if (payload.getCurveCode() == null || payload.getCurveCode().isBlank()) {
            throw new BizException("曲线编号不能为空");
        }
        ProcessCurve curve = new ProcessCurve();
        curve.setCurveCode(payload.getCurveCode());
        curve.setCurveName(payload.getCurveName());
        curve.setFabricType(payload.getFabricType());
        curve.setColorCode(payload.getColorCode());
        curve.setRecipeId(payload.getRecipeId());
        curve.setStartTemp(payload.getStartTemp() == null ? DEFAULT_START_TEMP : payload.getStartTemp());

        DraftCheck check = checkDraft(curve.getFabricType(), curve.getStartTemp(),
                payload.getNodes() == null ? List.of() : payload.getNodes());
        ensureValid(check);
        replaceNodes(curve, check.startTemp, check.inputs);
        return curveRepository.save(curve);
    }

    /**
     * 更新曲线（布种/起始温度/节点序列等草稿字段，拖动节点后保存）。
     * 先按草稿口径共用校验，不通过直接抛出，实体在本事务内不发生任何写入。
     */
    @Transactional
    public ProcessCurve saveNodes(Long id, CurvePayload payload) {
        ProcessCurve curve = detail(id);

        // 草稿口径：布种、起始温度与节点序列三者必须同时进入校验与保存链路
        String fabricType = payload.getFabricType() != null && !payload.getFabricType().isBlank()
                ? payload.getFabricType() : curve.getFabricType();
        BigDecimal startTemp = payload.getStartTemp() != null ? payload.getStartTemp() : curve.getStartTemp();
        List<CurveNodeInput> inputs = payload.getNodes() == null ? List.of() : payload.getNodes();
        DraftCheck check = checkDraft(fabricType, startTemp, inputs);
        ensureValid(check);

        // 校验通过后才回写表头
        if (payload.getCurveName() != null) {
            curve.setCurveName(payload.getCurveName());
        }
        curve.setFabricType(fabricType);
        if (payload.getColorCode() != null) {
            curve.setColorCode(payload.getColorCode());
        }
        if (payload.getRecipeId() != null) {
            curve.setRecipeId(payload.getRecipeId());
        }
        curve.setStartTemp(check.startTemp);
        replaceNodes(curve, check.startTemp, check.inputs);

        ProcessCurve saved = curveRepository.save(curve);
        executionOrderService.invalidateByCurve(id, "工艺曲线 " + saved.getCurveCode() + " 的节点或适用条件已改变，请重新创建开缸执行单");
        return saved;
    }

    /**
     * 预检：只算不存，返回每段速率、超限标记与逐项问题（不拦截、不落库）。
     * 草稿的布种、起始温度、节点序列与保存走同一套 {@link #checkDraft}，
     * 因此“预检通过 / 保存拒绝”按两套布种上限的分歧不会再出现。
     */
    @Transactional(readOnly = true)
    public Map<String, Object> validate(Long id, CurvePayload payload) {
        ProcessCurve curve = detail(id);
        String fabricType = payload.getFabricType() != null && !payload.getFabricType().isBlank()
                ? payload.getFabricType() : curve.getFabricType();
        BigDecimal startTemp = payload.getStartTemp() != null ? payload.getStartTemp() : curve.getStartTemp();
        // 预检请求未携带节点（空体反序列化得到空列表）时，按已保存节点序列试算；显式携带则完全以草稿为准
        List<CurveNodeInput> inputs = (payload.getNodes() == null || payload.getNodes().isEmpty())
                ? curveNodesToInputs(curve) : payload.getNodes();
        DraftCheck check = checkDraft(fabricType, startTemp, inputs);
        return check.toMap();
    }

    // ------------------------------------------------------------------
    // 预检与保存共用的草稿校验（纯计算，不读写曲线实体、不落库）
    // ------------------------------------------------------------------

    private List<CurveNodeInput> curveNodesToInputs(ProcessCurve curve) {
        List<CurveNodeInput> inputs = new ArrayList<>();
        for (CurveNode node : curve.getNodes()) {
            CurveNodeInput input = new CurveNodeInput();
            input.setId(node.getId());
            input.setStage(node.getStage());
            input.setTargetTemp(node.getTargetTemp());
            input.setDurationMin(node.getDurationMin());
            input.setMemo(node.getMemo());
            inputs.add(input);
        }
        return inputs;
    }

    /**
     * @param fabricType 草稿布种（为空则报布种档案缺失）
     * @param startTemp  草稿起始温度（为空按 30℃）
     * @param inputs     草稿节点序列（调用方保证非 null；空列表非法）
     */
    private DraftCheck checkDraft(String fabricType, BigDecimal startTemp, List<CurveNodeInput> inputs) {
        DraftCheck check = new DraftCheck();
        check.fabricType = fabricType;
        check.startTemp = startTemp == null ? DEFAULT_START_TEMP : startTemp;
        check.inputs = inputs;

        if (fabricType == null || fabricType.isBlank()) {
            check.errors.add(issue(null, "FABRIC_MISSING", "未指定适用布种，无法按布种档案校验升温速率与温度上限"));
        } else {
            fabricProfileRepository.findByFabricType(fabricType).ifPresentOrElse(
                    p -> check.profile = p,
                    () -> check.errors.add(issue(null, "FABRIC_PROFILE_MISSING",
                            "布种 " + fabricType + " 的工艺档案缺失（档案被停用或未建档），无法校验升温速率与温度上限")));
        }

        if (inputs.isEmpty()) {
            check.errors.add(issue(null, "NODES_EMPTY", "工艺曲线至少需要一个节点"));
        }

        // 起始温度本身也受布种保温上限约束（档案缺失时无法判断，跳过）
        if (check.profile != null && check.startTemp.compareTo(check.profile.getMaxHoldTemp()) > 0) {
            check.errors.add(issue(0, "START_TEMP_OVER_LIMIT",
                    "起始温度 " + check.startTemp + "℃ 超过布种 " + check.profile.getFabricName()
                            + " 允许的保温上限 " + check.profile.getMaxHoldTemp() + "℃"));
        }

        check.segments = computeSegments(check.startTemp, inputs, check.profile, check.errors);
        check.valid = check.errors.isEmpty();
        return check;
    }

    /** 保存链路：任何草稿问题都在写入前抛出（聚合全部节点与原因）。 */
    private void ensureValid(DraftCheck check) {
        if (!check.valid) {
            StringBuilder sb = new StringBuilder("工艺曲线草稿校验未通过（").append(check.errors.size()).append(" 项）：");
            for (int i = 0; i < check.errors.size(); i++) {
                if (i > 0) {
                    sb.append("；");
                }
                sb.append(check.errors.get(i).get("message"));
            }
            throw new BizException(sb.toString());
        }
    }

    private Map<String, Object> issue(Integer index, String code, String message) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("nodeIndex", index);
        m.put("code", code);
        m.put("message", message);
        return m;
    }

    private void replaceNodes(ProcessCurve curve, BigDecimal startTemp, List<CurveNodeInput> inputs) {
        curve.getNodes().clear();
        int totalMinutes = 0;
        BigDecimal maxRate = BigDecimal.ZERO;
        int seq = 1;
        for (CurveNodeInput input : inputs) {
            CurveNode node = new CurveNode();
            node.setCurve(curve);
            node.setSeqNo(seq++);
            node.setStage(input.getStage() == null ? "HOLD" : input.getStage());
            node.setTargetTemp(nvl(input.getTargetTemp()));
            node.setDurationMin(input.getDurationMin() == null ? 0 : input.getDurationMin());
            node.setMemo(input.getMemo());
            node.setRampRate(rateOf(curve.getNodes().isEmpty()
                            ? startTemp
                            : curve.getNodes().get(curve.getNodes().size() - 1).getTargetTemp(),
                    node.getTargetTemp(), node.getDurationMin()));
            curve.getNodes().add(node);
            totalMinutes += node.getDurationMin();
            if (node.getRampRate().abs().compareTo(maxRate.abs()) > 0) {
                maxRate = node.getRampRate();
            }
        }
        curve.setTotalMinutes(totalMinutes);
        curve.setMaxRampRate(maxRate.abs().setScale(2, RoundingMode.HALF_UP));
    }

    /**
     * 逐段计算速率（℃/min）与超限标记，并把零时长、升温超限、温度上限问题收集到 errors（带节点序号与原因）。
     * profile 为 null（布种档案缺失）时只做速率数学计算，不再叠加超限判断。
     */
    private List<Map<String, Object>> computeSegments(BigDecimal startTemp, List<CurveNodeInput> inputs,
                                                      FabricProfile profile, List<Map<String, Object>> errors) {
        List<Map<String, Object>> segments = new ArrayList<>();
        BigDecimal cursor = startTemp == null ? DEFAULT_START_TEMP : startTemp;
        int index = 1;
        for (CurveNodeInput input : inputs) {
            BigDecimal target = nvl(input.getTargetTemp());
            int duration = input.getDurationMin() == null ? 0 : input.getDurationMin();
            String stage = input.getStage() == null ? "HOLD" : input.getStage();

            List<String> codes = new ArrayList<>();
            // ---- 规则：节点时长必须大于 0 分钟 ----
            if (duration <= 0) {
                codes.add("ZERO_DURATION");
                errors.add(issue(index, "ZERO_DURATION",
                        "第 " + index + " 个节点（" + stageName(stage) + " → " + target + "℃）时长为 "
                                + duration + " 分钟，必须大于 0 分钟"));
            }
            // 速率：时长非法时记 0，避免除零，该节点仍继续参与温度上限判断
            BigDecimal rate = rateOf(cursor, target, duration);
            boolean overLimit = profile != null && duration > 0
                    && "RAMP".equalsIgnoreCase(stage)
                    && rate.compareTo(profile.getMaxRampRate()) > 0;
            boolean overTemp = profile != null && target.compareTo(profile.getMaxHoldTemp()) > 0;

            if (overLimit) {
                codes.add("RAMP_OVER_LIMIT");
                errors.add(issue(index, "RAMP_OVER_LIMIT",
                        "第 " + index + " 个节点（升温 " + cursor + "℃→" + target + "℃，" + duration
                                + " 分钟）升温速率 " + rate + "℃/min 超过布种 " + profile.getFabricName()
                                + "（" + profile.getFabricType() + "）允许上限 " + profile.getMaxRampRate()
                                + "℃/min，请放缓升温或延长该段时长"));
            }
            if (overTemp) {
                codes.add("TEMP_OVER_LIMIT");
                errors.add(issue(index, "TEMP_OVER_LIMIT",
                        "第 " + index + " 个节点（" + stageName(stage) + "）温度 " + target + "℃ 超过布种 "
                                + profile.getFabricName() + "（" + profile.getFabricType()
                                + "）允许的保温上限 " + profile.getMaxHoldTemp() + "℃"));
            }

            Map<String, Object> seg = new LinkedHashMap<>();
            seg.put("index", index);
            seg.put("stage", stage);
            seg.put("fromTemp", cursor);
            seg.put("toTemp", target);
            seg.put("durationMin", duration);
            seg.put("rate", rate);
            seg.put("maxRampRate", profile == null ? null : profile.getMaxRampRate());
            seg.put("maxHoldTemp", profile == null ? null : profile.getMaxHoldTemp());
            seg.put("overLimit", overLimit);
            seg.put("overTemp", overTemp);
            seg.put("issueCodes", codes);
            segments.add(seg);

            cursor = target;
            index++;
        }
        return segments;
    }

    private String stageName(String stage) {
        if ("RAMP".equalsIgnoreCase(stage)) {
            return "升温";
        }
        if ("COOL".equalsIgnoreCase(stage)) {
            return "降温";
        }
        return "保温";
    }

    private BigDecimal rateOf(BigDecimal from, BigDecimal to, int duration) {
        if (duration <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return to.subtract(from).divide(BigDecimal.valueOf(duration), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    /** 草稿校验中间结果（预检与保存共用）。 */
    private static final class DraftCheck {
        private String fabricType;
        private BigDecimal startTemp;
        private FabricProfile profile;
        private List<CurveNodeInput> inputs;
        private List<Map<String, Object>> segments = new ArrayList<>();
        private final List<Map<String, Object>> errors = new ArrayList<>();
        private boolean valid;

        private Map<String, Object> toMap() {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("fabricType", fabricType);
            data.put("fabricName", profile == null ? null : profile.getFabricName());
            data.put("startTemp", startTemp);
            data.put("maxRampRate", profile == null ? null : profile.getMaxRampRate());
            data.put("maxHoldTemp", profile == null ? null : profile.getMaxHoldTemp());
            data.put("valid", valid);
            data.put("segments", segments);
            data.put("errors", errors);
            return data;
        }
    }
}
