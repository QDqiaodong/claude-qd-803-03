package com.dyeing.mill.engine;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 清洗矩阵查询：先查版本内显式单元（fromFamily,toFamily），缺失时按
 * 「染料类 + 深浅层级」的同口径默认值兜底，并标记为兜底（解释里说明“矩阵未配置，按默认口径”）。
 * 规则版本兼容：旧方案复算时传入旧版本的 cells，本类逻辑不变，只取旧口径数值。
 */
public class WashMatrix {

    /** 同色/浅转深/深转浅/特殊切换的默认口径（分钟, 元） */
    private static final Map<String, int[]> DEFAULTS = Map.of(
            "SAME", new int[]{15, 60},
            "L2D", new int[]{25, 120},
            "D2L", new int[]{70, 400},
            "SPECIAL", new int[]{95, 600});

    private final Map<Long, ScheduleInput.EFamily> familyById;
    private final Map<String, ScheduleInput.EWashCell> cellIndex = new HashMap<>();

    public WashMatrix(List<ScheduleInput.EFamily> families, List<ScheduleInput.EWashCell> cells) {
        this.familyById = new HashMap<>();
        for (ScheduleInput.EFamily f : families) {
            familyById.put(f.getId(), f);
        }
        for (ScheduleInput.EWashCell cell : cells) {
            cellIndex.put(cell.getFromFamilyId() + ">" + cell.getToFamilyId(), cell);
        }
    }

    public ScheduleInput.EFamily family(Long id) {
        return familyById.get(id);
    }

    /**
     * 判定切换类型：同色族续染 SAME；涉及 SPECIAL 族一律 SPECIAL；
     * 其余按源/目标深浅 LIGHT→DARK = L2D，DARK→LIGHT = D2L，同族不同染料按深浅。
     */
    public String switchType(Long fromId, Long toId) {
        ScheduleInput.EFamily from = familyById.get(fromId);
        ScheduleInput.EFamily to = familyById.get(toId);
        if (from == null || to == null) {
            return "SAME";
        }
        if (from.getId().equals(to.getId())) {
            return "SAME";
        }
        if ("SPECIAL".equals(from.getDepthLevel()) || "SPECIAL".equals(to.getDepthLevel())) {
            return "SPECIAL";
        }
        boolean fromLight = "LIGHT".equals(from.getDepthLevel());
        boolean toDark = "DARK".equals(to.getDepthLevel());
        if (fromLight && toDark) {
            return "L2D";
        }
        if (!fromLight && !toDark) {
            // DARK -> LIGHT
            return "D2L";
        }
        // 同为浅或同为深但不同染料族：按同族切换轻处理
        return "SAME";
    }

    /** 查询结果（时长/成本 + 是否兜底默认） */
    public Lookup lookup(Long fromFamilyId, Long toFamilyId) {
        String type = switchType(fromFamilyId, toFamilyId);
        ScheduleInput.EWashCell explicit = cellIndex.get(fromFamilyId + ">" + toFamilyId);
        if (explicit != null) {
            String t = explicit.getSwitchType();
            return new Lookup(explicit.getWashMinutes(), explicit.getWashCost(),
                    t != null && !t.isBlank() ? t : type, false,
                    explicit.getRemark());
        }
        int[] d = DEFAULTS.getOrDefault(type, DEFAULTS.get("SPECIAL"));
        return new Lookup(d[0], BigDecimal.valueOf(d[1]), type, true,
                "矩阵未配置该格，按「" + typeName(type) + "」默认口径");
    }

    public static String typeName(String type) {
        return switch (type) {
            case "SAME" -> "同色续染";
            case "L2D" -> "浅转深";
            case "D2L" -> "深转浅";
            case "SPECIAL" -> "特殊染料切换";
            default -> type;
        };
    }

    /** 空缸/中性起步：首缸不产生清洗 */
    public Lookup neutralTo(Long toFamilyId) {
        return new Lookup(0, BigDecimal.ZERO, "SAME", false, "空缸中性起步，无需清洗");
    }

    public record Lookup(int minutes, BigDecimal cost, String switchType, boolean fallback, String note) {
    }
}
