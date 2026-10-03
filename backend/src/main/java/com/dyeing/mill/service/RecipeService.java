package com.dyeing.mill.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dyeing.mill.common.BizException;
import com.dyeing.mill.domain.Recipe;
import com.dyeing.mill.domain.RecipeItem;
import com.dyeing.mill.dto.ColorStatDTO;
import com.dyeing.mill.dto.RecipeItemInput;
import com.dyeing.mill.dto.RecipePayload;
import com.dyeing.mill.repository.ClothCartRepository;
import com.dyeing.mill.repository.RecipeRepository;

/**
 * 染色配方业务：明细 owf 合计 100% 校验、成本核算、版本演进。
 */
@Service
public class RecipeService {

    private static final BigDecimal FULL = new BigDecimal("100.000");
    private static final BigDecimal TOLERANCE = new BigDecimal("0.005");

    private final RecipeRepository recipeRepository;
    private final ClothCartRepository cartRepository;
    private final ExecutionOrderService executionOrderService;

    public RecipeService(RecipeRepository recipeRepository,
                         ClothCartRepository cartRepository,
                         ExecutionOrderService executionOrderService) {
        this.recipeRepository = recipeRepository;
        this.cartRepository = cartRepository;
        this.executionOrderService = executionOrderService;
    }

    public List<Recipe> list() {
        return recipeRepository.findAll();
    }

    public Recipe detail(Long id) {
        return recipeRepository.findById(id).orElseThrow(() -> new BizException("配方不存在或已删除：id=" + id));
    }

    /**
     * 新建配方。
     */
    @Transactional
    public Recipe create(RecipePayload payload) {
        if (payload.getRecipeCode() == null || payload.getRecipeCode().isBlank()) {
            throw new BizException("配方编号不能为空");
        }
        Recipe recipe = new Recipe();
        applyPayload(recipe, payload);
        recipe.setCreateTime(LocalDateTime.now());
        return recipeRepository.save(recipe);
    }

    /**
     * 更新配方（含整套明细行替换）。
     */
    @Transactional
    public Recipe update(Long id, RecipePayload payload) {
        Recipe recipe = detail(id);
        applyPayload(recipe, payload);
        Recipe saved = recipeRepository.save(recipe);
        executionOrderService.invalidateByRecipe(id, "配方 " + saved.getRecipeCode()
                + " 的发布状态、版本或明细已被编辑，请重新创建开缸执行单");
        return saved;
    }

    /**
     * 配方升版：复制出一张新版本草稿。
     */
    @Transactional
    public Recipe cloneVersion(Long id) {
        Recipe source = detail(id);
        Recipe copy = new Recipe();
        copy.setRecipeCode(source.getRecipeCode() + "-V" + ((source.getVersionNo() == null ? 1 : source.getVersionNo()) + 1));
        copy.setColorCode(source.getColorCode());
        copy.setColorName(source.getColorName());
        copy.setCustomer(source.getCustomer());
        copy.setFabricType(source.getFabricType());
        copy.setVersionNo((source.getVersionNo() == null ? 1 : source.getVersionNo()) + 1);
        copy.setColorHex(source.getColorHex());
        copy.setLabL(source.getLabL());
        copy.setLabA(source.getLabA());
        copy.setLabB(source.getLabB());
        copy.setStatus("DRAFT");
        copy.setCreateTime(LocalDateTime.now());
        copy.setTotalOwf(source.getTotalOwf());
        copy.setCostPerKg(source.getCostPerKg());
        copy.setDeleted(0);
        int seq = 1;
        for (RecipeItem item : source.getItems()) {
            RecipeItem newItem = new RecipeItem();
            newItem.setRecipe(copy);
            newItem.setSeqNo(seq++);
            newItem.setItemType(item.getItemType());
            newItem.setDyeName(item.getDyeName());
            newItem.setOwfPercent(item.getOwfPercent());
            newItem.setUnitPrice(item.getUnitPrice());
            newItem.setColorHex(item.getColorHex());
            newItem.setDeleted(0);
            copy.getItems().add(newItem);
        }
        return recipeRepository.save(copy);
    }

    @Transactional
    public void delete(Long id) {
        Recipe recipe = detail(id);
        recipe.setDeleted(1);
        recipeRepository.save(recipe);
        executionOrderService.invalidateByRecipe(id, "配方 " + recipe.getRecipeCode() + " 已删除，来源资料不可用");
    }

    /**
     * 按色号统计缸次。
     */
    public List<ColorStatDTO> colorStats() {
        return cartRepository.statByColorCode();
    }

    /**
     * 按客户统计配方数与平均成本。
     */
    public List<Map<String, Object>> customerStats() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Object[] row : recipeRepository.statByCustomer()) {
            BigDecimal avg = row[2] == null ? BigDecimal.ZERO : ((BigDecimal) row[2]).setScale(2, RoundingMode.HALF_UP);
            rows.add(Map.of("customer", row[0], "recipeCount", row[1], "avgCostPerKg", avg));
        }
        return rows;
    }

    private void applyPayload(Recipe recipe, RecipePayload payload) {
        recipe.setRecipeCode(payload.getRecipeCode());
        recipe.setColorCode(payload.getColorCode());
        recipe.setColorName(payload.getColorName());
        recipe.setCustomer(payload.getCustomer());
        recipe.setFabricType(payload.getFabricType());
        recipe.setVersionNo(payload.getVersionNo() == null ? 1 : payload.getVersionNo());
        recipe.setColorHex(payload.getColorHex());
        recipe.setLabL(payload.getLabL());
        recipe.setLabA(payload.getLabA());
        recipe.setLabB(payload.getLabB());
        recipe.setStatus(payload.getStatus() == null ? "DRAFT" : payload.getStatus());

        List<RecipeItemInput> inputs = payload.getItems() == null ? List.of() : payload.getItems();
        if (inputs.isEmpty()) {
            throw new BizException("配方明细不能为空，请至少填写一条染料");
        }

        // ---- 规则：染料明细 owf 合计必须 100%，超出或不足都要给出差值 ----
        BigDecimal dyeSum = BigDecimal.ZERO.setScale(3);
        for (RecipeItemInput input : inputs) {
            if ("DYE".equalsIgnoreCase(input.getItemType())) {
                dyeSum = dyeSum.add(nvl(input.getOwfPercent()));
            }
        }
        BigDecimal diff = dyeSum.subtract(FULL).abs();
        if (diff.compareTo(TOLERANCE) > 0) {
            BigDecimal gap = FULL.subtract(dyeSum);
            String direction = gap.compareTo(BigDecimal.ZERO) > 0 ? "还差" : "超出";
            throw new BizException("染料 owf 合计 " + dyeSum.setScale(3) + "%，"
                    + direction + " " + gap.abs().setScale(3) + "% 才到 100%，请调整配方阶梯后重试");
        }

        // 明细行整体替换，序号按传入顺序重建
        recipe.getItems().clear();
        BigDecimal cost = BigDecimal.ZERO;
        int seq = 1;
        for (RecipeItemInput input : inputs) {
            RecipeItem item = new RecipeItem();
            item.setRecipe(recipe);
            item.setSeqNo(seq++);
            item.setItemType(input.getItemType());
            item.setDyeName(input.getDyeName());
            item.setOwfPercent(nvl(input.getOwfPercent()));
            item.setUnitPrice(nvl(input.getUnitPrice()));
            item.setColorHex(input.getColorHex());
            item.setDeleted(0);
            recipe.getItems().add(item);
            // 成本 = Σ (owf% / 100) × 单价
            cost = cost.add(item.getOwfPercent()
                    .divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP)
                    .multiply(item.getUnitPrice()));
        }
        recipe.setTotalOwf(dyeSum.setScale(3, RoundingMode.HALF_UP));
        recipe.setCostPerKg(cost.setScale(2, RoundingMode.HALF_UP));
    }

    private BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
