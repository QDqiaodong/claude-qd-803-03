package com.dyeing.mill.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.dyeing.mill.common.BizException;
import com.dyeing.mill.domain.ClothCart;
import com.dyeing.mill.domain.CurveNode;
import com.dyeing.mill.domain.DyeVat;
import com.dyeing.mill.domain.ExecutionOrder;
import com.dyeing.mill.domain.ProcessCurve;
import com.dyeing.mill.domain.Recipe;
import com.dyeing.mill.domain.RecipeItem;
import com.dyeing.mill.dto.ExecutionOrderCreateRequest;
import com.dyeing.mill.repository.ClothCartRepository;
import com.dyeing.mill.repository.DyeVatRepository;
import com.dyeing.mill.repository.ExecutionOrderRepository;
import com.dyeing.mill.repository.ProcessCurveRepository;
import com.dyeing.mill.repository.RecipeRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.Data;

/**
 * 开缸执行单：把排缸布车、已发布配方和匹配工艺曲线串成可确认、可下达的开缸依据。
 */
@Service
public class ExecutionOrderService {

    public static final Map<String, BigDecimal> MACHINE_MAX_TEMP = Map.of(
            "OVERFLOW", BigDecimal.valueOf(135),
            "AIRFLOW", BigDecimal.valueOf(140),
            "JIG", BigDecimal.valueOf(98));

    private static final BigDecimal AMBIENT_TEMP = BigDecimal.valueOf(32);
    private static final BigDecimal METERS_TO_KG = new BigDecimal("0.18");

    private final ExecutionOrderRepository orderRepository;
    private final ClothCartRepository cartRepository;
    private final DyeVatRepository vatRepository;
    private final RecipeRepository recipeRepository;
    private final ProcessCurveRepository curveRepository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${mill.execution.fail-after-params:false}")
    private boolean failAfterParams;

    public ExecutionOrderService(ExecutionOrderRepository orderRepository,
                                 ClothCartRepository cartRepository,
                                 DyeVatRepository vatRepository,
                                 RecipeRepository recipeRepository,
                                 ProcessCurveRepository curveRepository,
                                 StringRedisTemplate redisTemplate,
                                 ObjectMapper objectMapper) {
        this.orderRepository = orderRepository;
        this.cartRepository = cartRepository;
        this.vatRepository = vatRepository;
        this.recipeRepository = recipeRepository;
        this.curveRepository = curveRepository;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public List<ExecutionOrder> list() {
        List<ExecutionOrder> orders = orderRepository.findAllOrderByStatus();
        for (ExecutionOrder order : orders) {
            if ("PENDING_CONFIRM".equals(order.getStatus())) {
                refreshPendingStatus(order, false);
            }
        }
        return orders;
    }

    @Transactional
    public ExecutionOrder detail(Long id) {
        ExecutionOrder order = orderRepository.findById(id)
                .orElseThrow(() -> new BizException("开缸执行单不存在：id=" + id));
        if ("PENDING_CONFIRM".equals(order.getStatus())) {
            refreshPendingStatus(order, true);
        }
        return order;
    }

    /** 只核对不落单：前端选择布车后展示具体差异。 */
    @Transactional(readOnly = true)
    public Map<String, Object> preview(ExecutionOrderCreateRequest request) {
        ResolvedSources sources = resolve(request, false);
        Map<String, Object> data = sourceView(sources);
        data.put("valid", sources.getDifferences().isEmpty());
        data.put("differences", sources.getDifferences());
        return data;
    }

    /**
     * 创建待确认单。任一资料条件不满足时返回所有具体差异，不能产生看似可用的单据。
     */
    @Transactional
    public ExecutionOrder create(ExecutionOrderCreateRequest request) {
        ResolvedSources sources = resolve(request, false);
        if (!sources.getDifferences().isEmpty()) {
            throw new BizException(String.join("；", sources.getDifferences()));
        }

        if (!orderRepository.findActiveByCart(sources.getCart().getId()).isEmpty()) {
            ExecutionOrder active = orderRepository.findActiveByCart(sources.getCart().getId()).get(0);
            if ("ISSUED".equals(active.getStatus())) {
                throw new BizException("布车 " + sources.getCart().getCartCode() + " 已由执行单 "
                        + active.getOrderCode() + " 下达，不能重复开缸");
            }
            throw new BizException("布车 " + sources.getCart().getCartCode() + " 已存在待确认执行单 "
                    + active.getOrderCode() + "，请确认或处理后再新建");
        }

        ExecutionOrder order = new ExecutionOrder();
        order.setOrderCode(generateOrderCode());
        order.setStatus("PENDING_CONFIRM");
        order.setCartId(sources.getCart().getId());
        order.setCartCode(sources.getCart().getCartCode());
        order.setVatId(sources.getVat().getId());
        order.setVatCode(sources.getVat().getVatCode());
        order.setRecipeId(sources.getRecipe().getId());
        order.setRecipeCode(sources.getRecipe().getRecipeCode());
        order.setRecipeVersionNo(sources.getRecipe().getVersionNo());
        order.setCurveId(sources.getCurve().getId());
        order.setCurveCode(sources.getCurve().getCurveCode());
        order.setSourceRevision(sources.getFingerprint());
        order.setEstimatedWeightKg(sources.getWeight());
        order.setEstimatedMinutes(sources.getCurve().getTotalMinutes());
        order.setKeyTemp(sources.getKeyTemp());
        order.setTargetTemp(sources.getKeyTemp());
        order.setHoldMinutes(sources.getHoldMinutes());
        order.setMachineMaxTemp(sources.getMachineMaxTemp());
        order.setCreatedBy(normalizeOperator(request.getCreatedBy(), "计划员"));
        order.setCreateTime(LocalDateTime.now());
        return orderRepository.save(order);
    }

    /**
     * 下达：布车行级锁串行同车并发，数据库生成列唯一索引兜底；库表与 Redis 参数失败均回滚/恢复。
     */
    @Transactional
    public ExecutionOrder issue(Long id, String confirmedBy) {
        ExecutionOrder order = orderRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BizException("开缸执行单不存在：id=" + id));
        cartRepository.findByIdForUpdate(order.getCartId())
                .orElseThrow(() -> new BizException("布车不存在或已报废：id=" + order.getCartId()));
        if ("ISSUED".equals(order.getStatus())) {
            throw new BizException("执行单 " + order.getOrderCode() + " 已由 " + nullToEmpty(order.getConfirmedBy()) + " 下达，请勿重复操作");
        }
        if ("INVALID".equals(order.getStatus())) {
            throw new BizException("执行单 " + order.getOrderCode() + " 已失效：" + nullToEmpty(order.getInvalidReason()));
        }

        ResolvedSources sources = resolve(toRecheckRequest(order), true);
        if (!sources.getDifferences().isEmpty()) {
            order.setStatus("INVALID");
            order.setInvalidReason(joinDifferences(sources.getDifferences()));
            orderRepository.save(order);
            throw new BizException("来源资料已变化，执行单已失效：" + order.getInvalidReason());
        }
        if (!order.getSourceRevision().equals(sources.getFingerprint())) {
            order.setStatus("INVALID");
            order.setInvalidReason("来源资料指纹已变化，请使用最新资料重新创建执行单");
            orderRepository.save(order);
            throw new BizException(order.getInvalidReason());
        }

        // 第二道保险：即使后来者已经创建过另一张待确认单，也不能重复下达同一布车。
        List<ExecutionOrder> issued = orderRepository.findIssuedByCart(order.getCartId());
        if (!issued.isEmpty()) {
            ExecutionOrder winner = issued.get(0);
            throw new BizException("布车 " + order.getCartCode() + " 已由执行单 " + winner.getOrderCode()
                    + "（下达人：" + nullToEmpty(winner.getConfirmedBy()) + "）下达，请刷新后查看已生效单据");
        }

        ClothCart cart = sources.getCart();
        DyeVat vat = sources.getVat();

        order.setStatus("ISSUED");
        LocalDateTime issueTime = LocalDateTime.now();
        order.setConfirmedBy(normalizeOperator(confirmedBy, "下达人"));
        order.setIssueTime(issueTime);

        cart.setStatus("DYEING");
        cart.setVatId(vat.getId());
        cart.setVatCode(vat.getVatCode());

        BigDecimal target = sources.getKeyTemp();
        int hold = sources.getHoldMinutes();
        vat.setTargetTemp(target);
        vat.setHoldMinutes(hold);
        vat.setHoldRemainMinutes(hold);
        if (target.compareTo(nvl(vat.getCurrentTemp())) > 0) {
            vat.setStatus("HEATING");
        } else if (target.compareTo(nvl(vat.getCurrentTemp())) < 0) {
            vat.setStatus("COOLING");
        } else {
            vat.setStatus("HOLDING");
        }
        order.setSnapshotJson(buildSnapshot(order, sources, confirmedBy, issueTime));

        cartRepository.save(cart);
        vatRepository.save(vat);
        orderRepository.save(order);
        publishVatParamsInTransaction(vat, target, hold);
        if (failAfterParams) {
            throw new BizException("模拟下达过程异常：验证执行单、布车和染缸数据库状态回滚，Redis 参数由事务完成回调恢复");
        }
        try {
            orderRepository.flush();
            cartRepository.flush();
            vatRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new BizException("布车 " + order.getCartCode() + " 已有下达生效的开缸执行单，请刷新页面查看");
        }
        return order;
    }

    /** 配方更新、归档或删除时，旧待确认单立即标为失效并阻止下达。 */
    @Transactional
    public void invalidateByRecipe(Long recipeId, String reason) {
        for (ExecutionOrder order : orderRepository.findPendingByRecipe(recipeId)) {
            markInvalid(order, reason);
        }
    }

    @Transactional
    public void invalidateByCurve(Long curveId, String reason) {
        for (ExecutionOrder order : orderRepository.findPendingByCurve(curveId)) {
            markInvalid(order, reason);
        }
    }

    @Transactional
    public void invalidateByCart(Long cartId, String reason) {
        for (ExecutionOrder order : orderRepository.findPendingByCart(cartId)) {
            markInvalid(order, reason);
        }
    }

    @Transactional(readOnly = true)
    public boolean hasIssuedOrder(Long cartId) {
        return !orderRepository.findActiveByCart(cartId).stream()
                .filter(o -> "ISSUED".equals(o.getStatus()))
                .toList()
                .isEmpty();
    }

    private ResolvedSources resolve(ExecutionOrderCreateRequest request, boolean recheck) {
        ResolvedSources sources = new ResolvedSources();
        ClothCart cart = cartRepository.findById(request.getCartId())
                .orElseThrow(() -> new BizException("布车不存在或已报废：id=" + request.getCartId()));
        sources.setCart(cart);

        if (cart.getVatId() == null || !"DYEING".equals(cart.getStatus())) {
            sources.getDifferences().add("布车 " + cart.getCartCode() + " 尚未完成排缸（当前状态："
                    + statusOrPending(cart.getStatus()) + "），不能创建开缸执行单");
        } else {
            vatRepository.findById(cart.getVatId()).ifPresentOrElse(sources::setVat, () ->
                    sources.getDifferences().add("布车排入的染缸 id=" + cart.getVatId() + " 不存在或已停用"));
        }

        List<Recipe> releasedRecipes = recipeRepository.findAll().stream()
                .filter(r -> "RELEASED".equals(r.getStatus()))
                .filter(r -> Objects.equals(cart.getColorCode(), r.getColorCode()))
                .filter(r -> Objects.equals(cart.getFabricType(), r.getFabricType()))
                .sorted(Comparator.comparing(Recipe::getVersionNo, Comparator.nullsLast(Integer::compareTo)).reversed())
                .toList();
        Recipe latestRecipe = latestReleasedRecipeOrNull(releasedRecipes);

        if (request.getRecipeId() != null) {
            Recipe selected = recipeRepository.findById(request.getRecipeId()).orElse(null);
            if (selected == null) {
                sources.getDifferences().add("所选配方不存在或已删除");
            } else {
                sources.setRecipe(selected);
                if (!"RELEASED".equals(selected.getStatus())) {
                    sources.getDifferences().add("配方 " + selected.getRecipeCode() + " 当前为 " + statusName(selected.getStatus()) + "，必须已发布才能开缸");
                }
                if (!Objects.equals(cart.getFabricType(), selected.getFabricType())) {
                    sources.getDifferences().add("布种不匹配：布车为 " + cart.getFabricType() + "，配方为 " + selected.getFabricType());
                }
                if (!Objects.equals(cart.getColorCode(), selected.getColorCode())) {
                    sources.getDifferences().add("色号不匹配：布车为 " + cart.getColorCode() + "，配方为 " + selected.getColorCode());
                }
                if (latestRecipe != null && !Objects.equals(latestRecipe.getId(), selected.getId())) {
                    sources.getDifferences().add("配方版本不是最新：当前选择 V" + selected.getVersionNo()
                            + "，最新已发布版本为 " + latestRecipe.getRecipeCode() + " V" + latestRecipe.getVersionNo());
                }
            }
        } else if (recheck) {
            sources.getDifferences().add("执行单缺少配方来源，无法按最新资料复核");
        } else if (latestRecipe == null) {
            sources.getDifferences().add("布车布种 " + cart.getFabricType() + "、色号 " + cart.getColorCode() + " 找不到已发布配方");
        } else {
            sources.setRecipe(latestRecipe);
        }

        List<ProcessCurve> curves = curveRepository.findAll().stream()
                .filter(c -> Objects.equals(cart.getFabricType(), c.getFabricType()))
                .filter(c -> Objects.equals(cart.getColorCode(), c.getColorCode()))
                .toList();
        if (request.getCurveId() != null) {
            ProcessCurve selected = curveRepository.findById(request.getCurveId()).orElse(null);
            if (selected == null) {
                sources.getDifferences().add("所选工艺曲线不存在或已删除");
            } else {
                sources.setCurve(selected);
                if (!Objects.equals(cart.getFabricType(), selected.getFabricType())) {
                    sources.getDifferences().add("曲线布种不匹配：布车为 " + cart.getFabricType() + "，曲线为 " + selected.getFabricType());
                }
                if (!Objects.equals(cart.getColorCode(), selected.getColorCode())) {
                    sources.getDifferences().add("曲线色号不匹配：布车为 " + cart.getColorCode() + "，曲线为 " + selected.getColorCode());
                }
                if (sources.getRecipe() != null && !Objects.equals(sources.getRecipe().getId(), selected.getRecipeId())) {
                    sources.getDifferences().add("工艺曲线未绑定所选配方：曲线配方 id=" + selected.getRecipeId()
                            + "，当前配方 id=" + sources.getRecipe().getId());
                }
            }
        } else if (recheck) {
            sources.getDifferences().add("执行单缺少曲线来源，无法按最新资料复核");
        } else {
            ProcessCurve matched = null;
            if (sources.getRecipe() != null) {
                matched = curves.stream()
                        .filter(c -> Objects.equals(sources.getRecipe().getId(), c.getRecipeId()))
                        .findFirst()
                        .orElse(null);
            }
            if (matched == null) {
                sources.getDifferences().add("找不到布种 " + cart.getFabricType() + "、色号 " + cart.getColorCode()
                        + " 且绑定配方 " + (sources.getRecipe() == null ? "--" : sources.getRecipe().getRecipeCode()) + " 的工艺曲线");
            } else {
                sources.setCurve(matched);
            }
        }

        if (sources.getVat() != null) {
            DyeVat vat = sources.getVat();
            BigDecimal machineMax = MACHINE_MAX_TEMP.get(vat.getMachineType());
            sources.setMachineMaxTemp(machineMax);
            if (machineMax == null) {
                sources.getDifferences().add("未知机台类型 " + vat.getMachineType() + "，无法核对温度上限");
            }
            if (vat.getCapacityKg() == null) {
                sources.getDifferences().add("染缸 " + vat.getVatCode() + " 缺少容量参数，无法核对布重");
            } else if (cart.getMeters() != null) {
                BigDecimal weight = cart.getMeters().multiply(METERS_TO_KG).setScale(2, RoundingMode.HALF_UP);
                sources.setWeight(weight);
                if (weight.compareTo(vat.getCapacityKg()) > 0) {
                    sources.getDifferences().add("染缸容量不满足：布车折算 " + weight + "kg，"
                            + vat.getVatCode() + " 容量仅 " + vat.getCapacityKg() + "kg，超出 "
                            + weight.subtract(vat.getCapacityKg()).setScale(2, RoundingMode.HALF_UP) + "kg");
                }
            } else {
                sources.getDifferences().add("布车缺少米数，无法折算布重并核对染缸容量");
            }
        }

        if (sources.getCurve() != null) {
            List<CurveNode> holdNodes = sources.getCurve().getNodes().stream()
                    .filter(n -> "HOLD".equalsIgnoreCase(n.getStage()))
                    .toList();
            if (holdNodes.isEmpty()) {
                sources.getDifferences().add("工艺曲线 " + sources.getCurve().getCurveCode() + " 没有保温节点，无法确定保温温度和时长");
            } else {
                BigDecimal keyTemp = holdNodes.stream()
                        .map(CurveNode::getTargetTemp)
                        .max(BigDecimal::compareTo)
                        .orElse(BigDecimal.ZERO);
                int holdMinutes = holdNodes.stream()
                        .map(n -> n.getDurationMin() == null ? 0 : n.getDurationMin())
                        .reduce(0, Integer::sum);
                sources.setKeyTemp(keyTemp);
                sources.setHoldMinutes(holdMinutes);
                if (sources.getMachineMaxTemp() != null && keyTemp.compareTo(sources.getMachineMaxTemp()) > 0) {
                    sources.getDifferences().add("机台温度上限不满足：曲线关键保温温度 " + keyTemp + "℃，"
                            + sources.getVat().getVatCode() + "（" + machineName(sources.getVat().getMachineType())
                            + "）上限仅 " + sources.getMachineMaxTemp() + "℃，超出 "
                            + keyTemp.subtract(sources.getMachineMaxTemp()).setScale(1, RoundingMode.HALF_UP) + "℃");
                }
            }
            if (sources.getCurve().getNodes().isEmpty()) {
                sources.getDifferences().add("工艺曲线没有节点，不能下达");
            }
        }

        sources.setFingerprint(sha256(joinFingerprint(cart, sources.getVat(), sources.getRecipe(), sources.getCurve())));
        return sources;
    }

    private Recipe latestReleasedRecipeOrNull(List<Recipe> recipes) {
        return recipes.isEmpty() ? null : recipes.get(0);
    }

    private ExecutionOrderCreateRequest toRecheckRequest(ExecutionOrder order) {
        ExecutionOrderCreateRequest request = new ExecutionOrderCreateRequest();
        request.setCartId(order.getCartId());
        request.setRecipeId(order.getRecipeId());
        request.setCurveId(order.getCurveId());
        return request;
    }

    private void refreshPendingStatus(ExecutionOrder order, boolean saveWhenInvalid) {
        ResolvedSources sources;
        try {
            sources = resolve(toRecheckRequest(order), true);
        } catch (BizException e) {
            markInvalid(order, e.getMessage());
            if (saveWhenInvalid) {
                orderRepository.save(order);
            }
            return;
        }
        if (!sources.getDifferences().isEmpty() || !order.getSourceRevision().equals(sources.getFingerprint())) {
            markInvalid(order, joinDifferences(sources.getDifferences()));
            if (saveWhenInvalid) {
                orderRepository.save(order);
            }
        }
    }

    private void markInvalid(ExecutionOrder order, String reason) {
        if (!"PENDING_CONFIRM".equals(order.getStatus())) {
            return;
        }
        order.setStatus("INVALID");
        order.setInvalidReason(reason == null || reason.isBlank() ? "来源资料已变化" : reason);
        orderRepository.save(order);
    }

    private String joinDifferences(List<String> differences) {
        return differences.isEmpty() ? "来源资料已变化" : String.join("；", differences);
    }

    private Map<String, Object> sourceView(ResolvedSources sources) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("cart", sourceMap(sources.getCart()));
        data.put("vat", sourceMap(sources.getVat()));
        data.put("recipe", sourceMap(sources.getRecipe()));
        data.put("curve", sourceMap(sources.getCurve()));
        data.put("estimatedWeightKg", sources.getWeight());
        data.put("estimatedMinutes", sources.getCurve() == null ? null : sources.getCurve().getTotalMinutes());
        data.put("keyTemp", sources.getKeyTemp());
        data.put("targetTemp", sources.getKeyTemp());
        data.put("holdMinutes", sources.getHoldMinutes());
        data.put("machineMaxTemp", sources.getMachineMaxTemp());
        data.put("fingerprint", sources.getFingerprint());
        return data;
    }

    private Map<String, Object> sourceMap(ClothCart cart) {
        if (cart == null) return null;
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", cart.getId());
        map.put("cartCode", cart.getCartCode());
        map.put("fabricType", cart.getFabricType());
        map.put("colorCode", cart.getColorCode());
        map.put("colorName", cart.getColorName());
        map.put("meters", cart.getMeters());
        map.put("vatId", cart.getVatId());
        map.put("vatCode", cart.getVatCode());
        map.put("status", cart.getStatus());
        map.put("dyeStartTime", cart.getDyeStartTime());
        map.put("dyeEndTime", cart.getDyeEndTime());
        return map;
    }

    private Map<String, Object> sourceMap(DyeVat vat) {
        if (vat == null) return null;
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", vat.getId());
        map.put("vatCode", vat.getVatCode());
        map.put("machineType", vat.getMachineType());
        map.put("capacityKg", vat.getCapacityKg());
        map.put("liquorRatio", vat.getLiquorRatio());
        map.put("status", vat.getStatus());
        map.put("currentTemp", vat.getCurrentTemp());
        map.put("targetTemp", vat.getTargetTemp());
        map.put("holdMinutes", vat.getHoldMinutes());
        map.put("workshop", vat.getWorkshop());
        return map;
    }

    private Map<String, Object> sourceMap(Recipe recipe) {
        if (recipe == null) return null;
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", recipe.getId());
        map.put("recipeCode", recipe.getRecipeCode());
        map.put("versionNo", recipe.getVersionNo());
        map.put("status", recipe.getStatus());
        map.put("colorCode", recipe.getColorCode());
        map.put("colorName", recipe.getColorName());
        map.put("fabricType", recipe.getFabricType());
        map.put("customer", recipe.getCustomer());
        map.put("totalOwf", recipe.getTotalOwf());
        map.put("costPerKg", recipe.getCostPerKg());
        map.put("items", recipe.getItems().stream().map(this::snapshotMap).toList());
        return map;
    }

    private Map<String, Object> sourceMap(ProcessCurve curve) {
        if (curve == null) return null;
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", curve.getId());
        map.put("curveCode", curve.getCurveCode());
        map.put("curveName", curve.getCurveName());
        map.put("fabricType", curve.getFabricType());
        map.put("colorCode", curve.getColorCode());
        map.put("recipeId", curve.getRecipeId());
        map.put("startTemp", curve.getStartTemp());
        map.put("totalMinutes", curve.getTotalMinutes());
        map.put("maxRampRate", curve.getMaxRampRate());
        map.put("nodes", curve.getNodes().stream().map(this::snapshotMap).toList());
        return map;
    }

    private Map<String, Object> snapshotMap(RecipeItem item) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("seqNo", item.getSeqNo());
        map.put("itemType", item.getItemType());
        map.put("dyeName", item.getDyeName());
        map.put("owfPercent", item.getOwfPercent());
        map.put("unitPrice", item.getUnitPrice());
        map.put("colorHex", item.getColorHex());
        return map;
    }

    private Map<String, Object> snapshotMap(CurveNode node) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("seqNo", node.getSeqNo());
        map.put("stage", node.getStage());
        map.put("targetTemp", node.getTargetTemp());
        map.put("durationMin", node.getDurationMin());
        map.put("rampRate", node.getRampRate());
        map.put("memo", node.getMemo());
        return map;
    }

    private String buildSnapshot(ExecutionOrder order, ResolvedSources sources, String confirmedBy, LocalDateTime issueTime) {
        try {
            Map<String, Object> snapshot = sourceView(sources);
            snapshot.put("orderCode", order.getOrderCode());
            snapshot.put("confirmedBy", normalizeOperator(confirmedBy, "下达人"));
            snapshot.put("issueTime", issueTime.toString());
            snapshot.put("machineMaxTemp", sources.getMachineMaxTemp());
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException e) {
            throw new BizException("生成工艺快照失败：" + e.getMessage());
        }
    }

    private String joinFingerprint(ClothCart cart, DyeVat vat, Recipe recipe, ProcessCurve curve) {
        List<String> parts = new ArrayList<>();
        if (cart != null) {
            parts.add("C:" + String.join("|",
                    str(cart.getId()), str(cart.getCartCode()), str(cart.getFabricType()), str(cart.getColorCode()),
                    str(cart.getMeters()), str(cart.getVatId()), str(cart.getVatCode()), str(cart.getStatus()),
                    str(cart.getDyeStartTime()), str(cart.getDyeEndTime()), str(cart.getReworkTimes()), str(cart.getDeleted())));
        }
        if (vat != null) {
            parts.add("V:" + String.join("|",
                    str(vat.getId()), str(vat.getVatCode()), str(vat.getMachineType()), str(vat.getCapacityKg()),
                    str(vat.getLiquorRatio()), str(vat.getTargetTemp()),
                    str(vat.getHoldMinutes()), str(vat.getDeleted())));
        }
        if (recipe != null) {
            parts.add("R:" + str(recipe.getId()) + ":" + str(recipe.getVersionNo()) + ":" + str(recipe.getStatus())
                    + ":" + str(recipe.getTotalOwf()) + ":" + str(recipe.getCostPerKg()) + ":" + str(recipe.getDeleted())
                    + ":" + recipe.getItems().stream().map(i -> String.join(",",
                            str(i.getSeqNo()), str(i.getItemType()), str(i.getDyeName()), str(i.getOwfPercent()),
                            str(i.getUnitPrice()), str(i.getDeleted())))
                    .collect(Collectors.joining(";")));
        }
        if (curve != null) {
            parts.add("P:" + str(curve.getId()) + ":" + str(curve.getStartTemp()) + ":" + str(curve.getTotalMinutes())
                    + ":" + str(curve.getMaxRampRate()) + ":" + str(curve.getDeleted())
                    + ":" + curve.getNodes().stream().map(n -> String.join(",",
                            str(n.getSeqNo()), str(n.getStage()), str(n.getTargetTemp()), str(n.getDurationMin()),
                            str(n.getRampRate()), str(n.getMemo())))
                    .collect(Collectors.joining(";")));
        }
        return String.join("||", parts);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Redis 是染缸参数下发通道。参数在数据库写入后、事务提交前写入；若此处失败，
     * Spring 回滚执行单、布车和染缸；若数据库最终提交失败，afterCompletion 反向恢复 Redis。
     */
    private void publishVatParamsInTransaction(DyeVat vat, BigDecimal target, int hold) {
        String targetKey = "mill:vat:" + vat.getVatCode() + ":target";
        String holdKey = "mill:vat:" + vat.getVatCode() + ":holdMinutes";
        String oldTarget = readRedis(targetKey);
        String oldHold = readRedis(holdKey);
        writeRedis(targetKey, target.toPlainString());
        writeRedis(holdKey, String.valueOf(hold));

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status == STATUS_ROLLED_BACK) {
                        restoreRedis(targetKey, oldTarget);
                        restoreRedis(holdKey, oldHold);
                    }
                }
            });
        }
    }

    private String readRedis(String key) {
        try {
            return redisTemplate.opsForValue().get(key);
        } catch (RuntimeException e) {
            throw new BizException("读取染缸原参数失败，已停止下达：" + e.getMessage());
        }
    }

    private void writeRedis(String key, String value) {
        try {
            redisTemplate.opsForValue().set(key, value);
        } catch (RuntimeException e) {
            throw new BizException("染缸参数下发失败，执行单、布车和染缸数据库变更将全部回滚：" + e.getMessage());
        }
    }

    private void restoreRedis(String key, String oldValue) {
        try {
            if (oldValue == null) {
                redisTemplate.delete(key);
            } else {
                redisTemplate.opsForValue().set(key, oldValue);
            }
        } catch (RuntimeException e) {
            throw new IllegalStateException("事务回滚后恢复染缸参数失败，需立即人工补偿 key=" + key, e);
        }
    }

    private String generateOrderCode() {
        LocalDateTime now = LocalDateTime.now();
        String suffix = String.format("%06d", Math.abs(System.nanoTime()) % 1_000_000);
        return "KG-" + now.getYear()
                + String.format("%02d", now.getMonthValue())
                + String.format("%02d", now.getDayOfMonth())
                + "-" + suffix;
    }

    private String normalizeOperator(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String trimmed = value.trim();
        return trimmed.length() > 32 ? trimmed.substring(0, 32) : trimmed;
    }

    private String nullToEmpty(String value) {
        return Optional.ofNullable(value).orElse("");
    }

    private BigDecimal nvl(BigDecimal value) {
        return value == null ? AMBIENT_TEMP : value;
    }

    private String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String statusOrPending(String status) {
        return status == null || status.isBlank() ? "PENDING" : status;
    }

    private String statusName(String status) {
        return switch (statusOrPending(status)) {
            case "DRAFT" -> "草稿";
            case "RELEASED" -> "已发布";
            case "ARCHIVED" -> "已归档";
            default -> status;
        };
    }

    private String machineName(String machineType) {
        return switch (machineType) {
            case "OVERFLOW" -> "溢喷机";
            case "AIRFLOW" -> "气流机";
            case "JIG" -> "卷染机";
            default -> machineType;
        };
    }

    @Data
    private static class ResolvedSources {
        private ClothCart cart;
        private DyeVat vat;
        private Recipe recipe;
        private ProcessCurve curve;
        private BigDecimal estimatedWeightKg;
        private BigDecimal keyTemp;
        private Integer holdMinutes;
        private BigDecimal machineMaxTemp;
        private String fingerprint;
        private final List<String> differences = new ArrayList<>();

        @SuppressWarnings("unused")
        private BigDecimal getWeight() {
            return estimatedWeightKg;
        }

        @SuppressWarnings("unused")
        private void setWeight(BigDecimal weight) {
            this.estimatedWeightKg = weight;
        }
    }
}
