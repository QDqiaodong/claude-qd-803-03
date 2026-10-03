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
 */
@Service
public class CurveService {

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
        curve.setStartTemp(payload.getStartTemp() == null ? BigDecimal.valueOf(30) : payload.getStartTemp());
        applyNodes(curve, payload.getNodes());
        return curveRepository.save(curve);
    }

    /**
     * 更新曲线节点（拖动节点后保存）。
     */
    @Transactional
    public ProcessCurve saveNodes(Long id, CurvePayload payload) {
        ProcessCurve curve = detail(id);
        if (payload.getCurveName() != null) {
            curve.setCurveName(payload.getCurveName());
        }
        if (payload.getFabricType() != null) {
            curve.setFabricType(payload.getFabricType());
        }
        if (payload.getColorCode() != null) {
            curve.setColorCode(payload.getColorCode());
        }
        if (payload.getRecipeId() != null) {
            curve.setRecipeId(payload.getRecipeId());
        }
        if (payload.getStartTemp() != null) {
            curve.setStartTemp(payload.getStartTemp());
        }
        applyNodes(curve, payload.getNodes());
        ProcessCurve saved = curveRepository.save(curve);
        executionOrderService.invalidateByCurve(id, "工艺曲线 " + saved.getCurveCode() + " 的节点或适用条件已改变，请重新创建开缸执行单");
        return saved;
    }

    /**
     * 只看不算：返回每段速率与是否超限，供编辑器实时提示（不拦截）。
     */
    public Map<String, Object> validate(Long id, CurvePayload payload) {
        ProcessCurve curve = detail(id);
        FabricProfile profile = profileOf(curve.getFabricType());
        List<CurveNodeInput> inputs = payload.getNodes() == null ? curveNodesToInputs(curve) : payload.getNodes();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("fabricType", curve.getFabricType());
        data.put("fabricName", profile.getFabricName());
        data.put("maxRampRate", profile.getMaxRampRate());
        data.put("segments", computeSegments(curve.getStartTemp(), inputs, profile));
        return data;
    }

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

    private FabricProfile profileOf(String fabricType) {
        return fabricProfileRepository.findByFabricType(fabricType)
                .orElseThrow(() -> new BizException("未找到布种 " + fabricType + " 的工艺档案，无法校验升温速率"));
    }

    private void applyNodes(ProcessCurve curve, List<CurveNodeInput> inputs) {
        if (inputs == null || inputs.isEmpty()) {
            throw new BizException("工艺曲线至少需要一个节点");
        }
        FabricProfile profile = profileOf(curve.getFabricType());
        BigDecimal startTemp = curve.getStartTemp() == null ? BigDecimal.valueOf(30) : curve.getStartTemp();

        List<Map<String, Object>> segments = computeSegments(startTemp, inputs, profile);

        // ---- 规则：升温速率不得超过布种允许上限 ----
        for (Map<String, Object> seg : segments) {
            if (Boolean.TRUE.equals(seg.get("overLimit"))) {
                throw new BizException("第 " + seg.get("index") + " 段升温速率 " + seg.get("rate")
                        + "℃/min 超过布种 " + profile.getFabricName() + "（" + profile.getFabricType()
                        + "）允许上限 " + profile.getMaxRampRate() + "℃/min，请放缓升温或延长该段时长");
            }
            if (Boolean.TRUE.equals(seg.get("overTemp"))) {
                throw new BizException("第 " + seg.get("index") + " 个节点温度 " + seg.get("toTemp")
                        + "℃ 超过布种 " + profile.getFabricName() + " 允许的保温上限 "
                        + profile.getMaxHoldTemp() + "℃");
            }
        }

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
     * 逐段计算速率（℃/min）与超限标记。
     */
    private List<Map<String, Object>> computeSegments(BigDecimal startTemp, List<CurveNodeInput> inputs,
                                                      FabricProfile profile) {
        List<Map<String, Object>> segments = new ArrayList<>();
        BigDecimal cursor = startTemp == null ? BigDecimal.valueOf(30) : startTemp;
        int index = 1;
        for (CurveNodeInput input : inputs) {
            BigDecimal target = nvl(input.getTargetTemp());
            int duration = input.getDurationMin() == null ? 0 : input.getDurationMin();
            if (duration <= 0) {
                throw new BizException("第 " + index + " 个节点的时长必须大于 0 分钟");
            }
            BigDecimal rate = rateOf(cursor, target, duration);
            String stage = input.getStage() == null ? "HOLD" : input.getStage();
            boolean overLimit = "RAMP".equalsIgnoreCase(stage) && rate.compareTo(profile.getMaxRampRate()) > 0;
            boolean overTemp = target.compareTo(profile.getMaxHoldTemp()) > 0;

            Map<String, Object> seg = new LinkedHashMap<>();
            seg.put("index", index);
            seg.put("stage", stage);
            seg.put("fromTemp", cursor);
            seg.put("toTemp", target);
            seg.put("durationMin", duration);
            seg.put("rate", rate);
            seg.put("maxRampRate", profile.getMaxRampRate());
            seg.put("overLimit", overLimit);
            seg.put("overTemp", overTemp);
            segments.add(seg);

            cursor = target;
            index++;
        }
        return segments;
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
}
