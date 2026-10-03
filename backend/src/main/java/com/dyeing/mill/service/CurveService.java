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
 * 预检（validate）与保存（saveNodes/create）共用同一份「草稿解析 + 规则计算」：
 * 草稿 = 请求里携带的布种/起始温度/节点序列，缺省字段回落到曲线已保存值。
 * 因此编辑器里改完布种、拖完节点后看到的提示，与保存时的拦截口径完全一致。
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
        applyNodes(curve, payload.getNodes());
        return curveRepository.save(curve);
    }

    /**
     * 更新曲线节点（拖动节点后保存）。布种/起始温度/节点序列以草稿为准，
     * 校验不通过时整体回滚，库内保持原样。
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
     * 只看不算：按草稿（布种/起始温度/节点序列）实时校验并返回每段速率与问题清单，不落库。
     * 请求缺省的字段回落到已保存值，兼容只传节点序列的旧调用。
     */
    @Transactional(readOnly = true)
    public Map<String, Object> validate(Long id, CurvePayload payload) {
        ProcessCurve curve = detail(id);
        CurveDraft draft = resolveDraft(curve, payload);
        FabricProfile profile = profileOf(draft.fabricType());

        List<Map<String, Object>> segments = computeSegments(draft.startTemp(), draft.nodes(), profile);
        List<String> problems = segmentProblems(segments, profile);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("fabricType", draft.fabricType());
        data.put("fabricName", profile.getFabricName());
        data.put("maxRampRate", profile.getMaxRampRate());
        data.put("maxHoldTemp", profile.getMaxHoldTemp());
        data.put("startTemp", draft.startTemp());
        data.put("segments", segments);
        data.put("errors", problems);
        data.put("valid", problems.isEmpty());
        return data;
    }

    /**
     * 解析草稿：预检与保存都以此为准。请求未携带的字段回落到曲线已保存值；
     * 节点序列缺省（null 或空）时按已保存节点校验。
     */
    private CurveDraft resolveDraft(ProcessCurve curve, CurvePayload payload) {
        String fabricType = payload.getFabricType() == null || payload.getFabricType().isBlank()
                ? curve.getFabricType() : payload.getFabricType();
        BigDecimal startTemp = payload.getStartTemp() != null ? payload.getStartTemp()
                : (curve.getStartTemp() == null ? DEFAULT_START_TEMP : curve.getStartTemp());
        List<CurveNodeInput> nodes = payload.getNodes() == null || payload.getNodes().isEmpty()
                ? curveNodesToInputs(curve) : payload.getNodes();
        return new CurveDraft(fabricType, startTemp, nodes);
    }

    private record CurveDraft(String fabricType, BigDecimal startTemp, List<CurveNodeInput> nodes) {
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
                .orElseThrow(() -> new BizException("布种 " + fabricType
                        + " 缺少有效工艺档案（不存在或已停用），无法校验升温速率与温度上限，请先在布种档案中维护"));
    }

    private void applyNodes(ProcessCurve curve, List<CurveNodeInput> inputs) {
        if (inputs == null || inputs.isEmpty()) {
            throw new BizException("工艺曲线至少需要一个节点");
        }
        FabricProfile profile = profileOf(curve.getFabricType());
        BigDecimal startTemp = curve.getStartTemp() == null ? DEFAULT_START_TEMP : curve.getStartTemp();

        // ---- 与预检共用的同一套规则：零时长节点 / 升温超限 / 温度上限 ----
        List<String> problems = segmentProblems(computeSegments(startTemp, inputs, profile), profile);
        if (!problems.isEmpty()) {
            throw new BizException(problems.get(0));
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
     * 逐段计算速率（℃/min）与超限标记。预检与保存共用，保证两条链路口径一致。
     */
    private List<Map<String, Object>> computeSegments(BigDecimal startTemp, List<CurveNodeInput> inputs,
                                                      FabricProfile profile) {
        List<Map<String, Object>> segments = new ArrayList<>();
        BigDecimal cursor = startTemp == null ? DEFAULT_START_TEMP : startTemp;
        int index = 1;
        for (CurveNodeInput input : inputs) {
            BigDecimal target = nvl(input.getTargetTemp());
            int duration = input.getDurationMin() == null ? 0 : input.getDurationMin();
            boolean zeroDuration = duration <= 0;
            BigDecimal rate = rateOf(cursor, target, duration);
            String stage = input.getStage() == null ? "HOLD" : input.getStage();
            boolean overLimit = !zeroDuration && "RAMP".equalsIgnoreCase(stage)
                    && rate.compareTo(profile.getMaxRampRate()) > 0;
            boolean overTemp = target.compareTo(profile.getMaxHoldTemp()) > 0;

            Map<String, Object> seg = new LinkedHashMap<>();
            seg.put("index", index);
            seg.put("stage", stage);
            seg.put("fromTemp", cursor);
            seg.put("toTemp", target);
            seg.put("durationMin", duration);
            seg.put("rate", rate);
            seg.put("maxRampRate", profile.getMaxRampRate());
            seg.put("zeroDuration", zeroDuration);
            seg.put("overLimit", overLimit);
            seg.put("overTemp", overTemp);
            segments.add(seg);

            cursor = target;
            index++;
        }
        return segments;
    }

    /**
     * 逐段生成问题描述（指出第几个节点/第几段 + 原因）。
     * 预检把全量清单返回给编辑器，保存抛第一条拦截，两处措辞一致。
     */
    private List<String> segmentProblems(List<Map<String, Object>> segments, FabricProfile profile) {
        String fabricLabel = profile.getFabricName() + "（" + profile.getFabricType() + "）";
        List<String> problems = new ArrayList<>();
        for (Map<String, Object> seg : segments) {
            if (Boolean.TRUE.equals(seg.get("zeroDuration"))) {
                problems.add("第 " + seg.get("index") + " 个节点的时长必须大于 0 分钟，否则无法计算升温速率");
            }
            if (Boolean.TRUE.equals(seg.get("overLimit"))) {
                problems.add("第 " + seg.get("index") + " 段升温速率 " + seg.get("rate")
                        + "℃/min 超过布种 " + fabricLabel + " 允许上限 " + profile.getMaxRampRate()
                        + "℃/min，请放缓升温或延长该段时长");
            }
            if (Boolean.TRUE.equals(seg.get("overTemp"))) {
                problems.add("第 " + seg.get("index") + " 个节点温度 " + seg.get("toTemp")
                        + "℃ 超过布种 " + fabricLabel + " 允许的保温上限 " + profile.getMaxHoldTemp() + "℃");
            }
        }
        return problems;
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
