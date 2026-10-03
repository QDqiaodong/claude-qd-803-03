package com.dyeing.mill.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.dyeing.mill.domain.ClothCart;
import com.dyeing.mill.domain.ColorFamily;
import com.dyeing.mill.domain.ColorFamilyMap;
import com.dyeing.mill.domain.DyeVat;
import com.dyeing.mill.domain.FabricMachine;
import com.dyeing.mill.domain.MaintenanceWindow;
import com.dyeing.mill.domain.ProcessCurve;
import com.dyeing.mill.domain.Recipe;
import com.dyeing.mill.domain.ScheduleLock;
import com.dyeing.mill.domain.WashRule;
import com.dyeing.mill.domain.WashRuleVersion;
import com.dyeing.mill.engine.ScheduleInput;
import com.dyeing.mill.engine.ScheduleInput.EBlock;

/**
 * 把数据库现场数据（或历史快照 JSON）装配成引擎输入 ScheduleInput。
 * 快照即当时全量输入，规则版本升级后复算旧方案仍取旧版矩阵 + 旧快照，保证“按当时版本复算并显示原解释”。
 */
@Component
public class ScheduleInputAssembler {

    public static final BigDecimal METERS_TO_KG = new BigDecimal("0.18");

    private final ScheduleSnapshots snapshots;

    public ScheduleInputAssembler(ScheduleSnapshots snapshots) {
        this.snapshots = snapshots;
    }

    /** 用当前数据库数据构建输入。ruleVersion 为本次采用的清洗规则口径。 */
    public ScheduleInput fromLiveData(LocalDateTime anchor,
                                      LocalDateTime horizonEnd,
                                      WashRuleVersion ruleVersion,
                                      List<ClothCart> carts,
                                      List<DyeVat> vats,
                                      List<ColorFamily> families,
                                      List<ColorFamilyMap> familyMaps,
                                      List<Recipe> recipes,
                                      List<ProcessCurve> curves,
                                      List<FabricMachine> fabricMachines,
                                      List<MaintenanceWindow> maintenance,
                                      List<ScheduleLock> locks) {
        ScheduleInput in = base(anchor, horizonEnd);
        fillStatic(in, ruleVersion, vats, families, fabricMachines);

        Map<String, Long> colorToFamily = familyMaps.stream()
                .collect(Collectors.toMap(ColorFamilyMap::getColorCode, ColorFamilyMap::getFamilyId, (a, b) -> a));
        Map<Long, String> familyCodeById = families.stream()
                .collect(Collectors.toMap(ColorFamily::getId, ColorFamily::getFamilyCode, (a, b) -> a));
        Map<String, Recipe> releasedIndex = recipes.stream()
                .filter(r -> "RELEASED".equals(r.getStatus()))
                .collect(Collectors.toMap(r -> r.getFabricType() + "|" + r.getColorCode(), r -> r, (a, b) -> a));
        Map<String, ProcessCurve> curveIndex = curves.stream()
                .collect(Collectors.toMap(c -> c.getFabricType() + "|" + c.getColorCode(), c -> c,
                        (a, b) -> a.getTotalMinutes() != null && b.getTotalMinutes() != null
                                && a.getTotalMinutes() >= b.getTotalMinutes() ? a : b));

        for (ClothCart cart : carts) {
            ScheduleInput.ECart ec = toEngineCart(cart, colorToFamily, familyCodeById, releasedIndex, curveIndex);
            in.getCarts().add(ec);
        }

        for (MaintenanceWindow w : maintenance) {
            in.getMaintenance().add(block(w.getVatId(), w.getVatCode(), w.getStartTime(), w.getEndTime(),
                    "维护：" + w.getReason(), "MAINTENANCE", null, null, null));
        }
        for (ScheduleLock l : locks) {
            Long familyId = l.getCartId() == null ? null : familyOfCart(l.getCartId(), carts, colorToFamily);
            in.getLocks().add(block(l.getVatId(), l.getVatCode(), l.getStartTime(), l.getEndTime(),
                    l.getLockReason(), "LOCK", l.getCartId(), l.getCartCode(), familyId));
        }
        // 锚点时仍在制的缸段（固定、不可移动；并给缸提供当前色族）
        for (ClothCart cart : carts) {
            if ("DYEING".equals(cart.getStatus()) && cart.getVatId() != null
                    && cart.getDyeEndTime() != null && cart.getDyeEndTime().isAfter(anchor)) {
                in.getRunning().add(block(cart.getVatId(), cart.getVatCode(),
                        cart.getDyeStartTime(), cart.getDyeEndTime(),
                        "在制 " + cart.getCartCode(), "RUNNING",
                        cart.getId(), cart.getCartCode(), colorToFamily.get(cart.getColorCode())));
            }
        }
        return in;
    }

    /** 用历史快照 + 指定规则版本复算（版本兼容：旧方案取它保存的 ruleVersionId）。 */
    public ScheduleInput fromSnapshot(String snapshotJson, WashRuleVersion ruleVersion) {
        ScheduleSnapshots.Snapshot snap = snapshots.parse(snapshotJson);
        ScheduleInput in = base(snap.anchor, snap.horizonEnd);
        for (ScheduleSnapshots.JsonVat j : snap.vats) {
            ScheduleInput.EVat v = new ScheduleInput.EVat();
            v.setId(j.id);
            v.setCode(j.code);
            v.setMachineType(j.machineType);
            v.setCapacityKg(j.capacityKg);
            v.setWorkshop(j.workshop);
            in.getVats().add(v);
        }
        for (ScheduleSnapshots.JsonFamily f : snap.families) {
            ScheduleInput.EFamily ef = new ScheduleInput.EFamily();
            ef.setId(f.id);
            ef.setCode(f.code);
            ef.setName(f.name);
            ef.setDepthLevel(f.depthLevel);
            ef.setDyeClass(f.dyeClass);
            ef.setColorHex(f.colorHex);
            in.getFamilies().add(ef);
        }
        fillWashCells(in, ruleVersion);
        for (ScheduleSnapshots.JsonFabricMachine fm : snap.fabricMachines) {
            ScheduleInput.EFabricMachine efm = new ScheduleInput.EFabricMachine();
            efm.setFabricType(fm.fabricType);
            efm.setMachineType(fm.machineType);
            efm.setPreferred(fm.preferred);
            in.getFabricMachines().add(efm);
        }
        for (ScheduleSnapshots.JsonCart c : snap.carts) {
            ScheduleInput.ECart ec = new ScheduleInput.ECart();
            ec.setId(c.id);
            ec.setCode(c.code);
            ec.setFabricType(c.fabricType);
            ec.setColorCode(c.colorCode);
            ec.setColorName(c.colorName);
            ec.setMeters(c.meters);
            ec.setWeightKg(c.weightKg);
            ec.setCustomer(c.customer);
            ec.setStatus(c.status);
            ec.setDueTime(c.due);
            ec.setRush(c.rush);
            ec.setFamilyId(c.familyId);
            ec.setFamilyCode(c.familyCode);
            ec.setProcessMinutes(c.processMinutes);
            ec.setRecipeReady(c.recipeReady);
            ec.setRecipeIssue(c.recipeIssue);
            ec.setReworkTimes(c.reworkTimes);
            in.getCarts().add(ec);
        }
        for (ScheduleSnapshots.JsonBlock b : snap.maintenance) {
            in.getMaintenance().add(block(b.vatId, b.vatCode, b.start, b.end, b.reason, "MAINTENANCE", null, null, null));
        }
        for (ScheduleSnapshots.JsonBlock b : snap.locks) {
            in.getLocks().add(block(b.vatId, b.vatCode, b.start, b.end, b.reason, "LOCK",
                    b.cartId, b.cartCode, b.familyId));
        }
        for (ScheduleSnapshots.JsonBlock b : snap.running) {
            in.getRunning().add(block(b.vatId, b.vatCode, b.start, b.end, b.reason, "RUNNING",
                    b.cartId, b.cartCode, b.familyId));
        }
        return in;
    }

    private ScheduleInput base(LocalDateTime anchor, LocalDateTime horizonEnd) {
        ScheduleInput in = new ScheduleInput();
        in.setAnchor(anchor);
        in.setHorizonEnd(horizonEnd);
        return in;
    }

    private void fillStatic(ScheduleInput in, WashRuleVersion ruleVersion, List<DyeVat> vats,
                            List<ColorFamily> families, List<FabricMachine> fabricMachines) {
        for (DyeVat vat : vats) {
            ScheduleInput.EVat ev = new ScheduleInput.EVat();
            ev.setId(vat.getId());
            ev.setCode(vat.getVatCode());
            ev.setMachineType(vat.getMachineType());
            ev.setCapacityKg(vat.getCapacityKg());
            ev.setWorkshop(vat.getWorkshop());
            in.getVats().add(ev);
        }
        for (ColorFamily f : families) {
            ScheduleInput.EFamily ef = new ScheduleInput.EFamily();
            ef.setId(f.getId());
            ef.setCode(f.getFamilyCode());
            ef.setName(f.getFamilyName());
            ef.setDepthLevel(f.getDepthLevel());
            ef.setDyeClass(f.getDyeClass());
            ef.setColorHex(f.getColorHex());
            in.getFamilies().add(ef);
        }
        fillWashCells(in, ruleVersion);
        for (FabricMachine fm : fabricMachines) {
            ScheduleInput.EFabricMachine efm = new ScheduleInput.EFabricMachine();
            efm.setFabricType(fm.getFabricType());
            efm.setMachineType(fm.getMachineType());
            efm.setPreferred(fm.getPreferred() != null && fm.getPreferred() == 1);
            in.getFabricMachines().add(efm);
        }
        // 首选机型排前
        in.getFabricMachines().sort(Comparator.comparing((ScheduleInput.EFabricMachine m) -> m.isPreferred()).reversed());
    }

    private void fillWashCells(ScheduleInput in, WashRuleVersion ruleVersion) {
        for (WashRule r : ruleVersion.getRules()) {
            ScheduleInput.EWashCell cell = new ScheduleInput.EWashCell();
            cell.setFromFamilyId(r.getFromFamilyId());
            cell.setToFamilyId(r.getToFamilyId());
            cell.setSwitchType(r.getSwitchType());
            cell.setWashMinutes(r.getWashMinutes());
            cell.setWashCost(r.getWashCost());
            cell.setRemark(r.getRemark());
            in.getWashCells().add(cell);
        }
    }

    private ScheduleInput.ECart toEngineCart(ClothCart cart, Map<String, Long> colorToFamily,
                                             Map<Long, String> familyCodeById,
                                             Map<String, Recipe> releasedIndex,
                                             Map<String, ProcessCurve> curveIndex) {
        ScheduleInput.ECart ec = new ScheduleInput.ECart();
        ec.setId(cart.getId());
        ec.setCode(cart.getCartCode());
        ec.setFabricType(cart.getFabricType());
        ec.setColorCode(cart.getColorCode());
        ec.setColorName(cart.getColorName());
        ec.setMeters(cart.getMeters());
        ec.setCustomer(cart.getCustomer());
        ec.setStatus(cart.getStatus());
        ec.setDueTime(cart.getDueTime());
        ec.setRush(cart.getRushFlag() != null && cart.getRushFlag() == 1);
        ec.setReworkTimes(cart.getReworkTimes());
        BigDecimal weight = (cart.getMeters() == null ? BigDecimal.ZERO : cart.getMeters())
                .multiply(METERS_TO_KG);
        ec.setWeightKg(weight);

        Long familyId = colorToFamily.get(cart.getColorCode());
        ec.setFamilyId(familyId);
        if (familyId != null) {
            ec.setFamilyCode(familyCodeById.get(familyId));
        }

        String key = cart.getFabricType() + "|" + cart.getColorCode();
        Recipe recipe = releasedIndex.get(key);
        if (recipe != null) {
            ec.setRecipeReady(true);
        } else {
            ec.setRecipeReady(false);
            ec.setRecipeIssue("色号 " + cart.getColorCode() + " 在布种 " + cart.getFabricType()
                    + " 下没有已发布（RELEASED）配方");
        }
        ProcessCurve curve = curveIndex.get(key);
        if (curve != null && curve.getTotalMinutes() != null && curve.getTotalMinutes() > 0) {
            ec.setProcessMinutes(curve.getTotalMinutes());
        } else {
            ec.setProcessMinutes(defaultProcessMinutes(cart.getFabricType()));
        }
        return ec;
    }

    /** 找不到匹配曲线时的布种默认时长（排程仍可进行，解释里注明默认口径）。 */
    private int defaultProcessMinutes(String fabricType) {
        return switch (fabricType == null ? "" : fabricType) {
            case "POLY" -> 155;
            case "SILK" -> 135;
            case "NYLON", "BLEND" -> 120;
            case "VISCOSE" -> 125;
            default -> 115;
        };
    }

    private Long familyOfCart(Long cartId, List<ClothCart> carts, Map<String, Long> colorToFamily) {
        return carts.stream().filter(c -> Objects.equals(c.getId(), cartId)).findFirst()
                .map(c -> colorToFamily.get(c.getColorCode())).orElse(null);
    }

    private EBlock block(Long vatId, String vatCode, LocalDateTime start, LocalDateTime end, String reason,
                         String kind, Long cartId, String cartCode, Long familyId) {
        EBlock b = new EBlock();
        b.setVatId(vatId);
        b.setVatCode(vatCode);
        b.setStart(start);
        b.setEnd(end);
        b.setReason(reason);
        b.setKind(kind);
        b.setCartId(cartId);
        b.setCartCode(cartCode);
        b.setFamilyId(familyId);
        return b;
    }

    /** 引擎输入转快照行（持久化到方案 input_snapshot_json）。 */
    public Map<String, Object> toSnapshotMap(ScheduleInput in) {
        return snapshots.fromInput(in);
    }
}
