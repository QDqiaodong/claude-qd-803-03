package com.dyeing.mill.engine;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import lombok.Data;

/**
 * 排程引擎输入（纯 POJO，可由数据库现场数据或历史快照构建 —— 规则版本兼容的关键）。
 */
@Data
public class ScheduleInput {

    /** 排程锚点：只重算锚点之后、未锁定的区段 */
    private LocalDateTime anchor;
    /** 排程视窗结束：超出视窗仍放不下的布车按原因单列 */
    private LocalDateTime horizonEnd;

    private List<ECart> carts = new ArrayList<>();
    private List<EVat> vats = new ArrayList<>();
    /** 色族表：id → 色族 */
    private List<EFamily> families = new ArrayList<>();
    /** 清洗规则（某一规则版本下的完整矩阵单元） */
    private List<EWashCell> washCells = new ArrayList<>();
    /** 适配的机型集合：fabricType -> machineType 列表（有序，preferred 在前） */
    private List<EFabricMachine> fabricMachines = new ArrayList<>();
    /** 维护时段 */
    private List<EBlock> maintenance = new ArrayList<>();
    /** 人工锁定段（含仅封缸） */
    private List<EBlock> locks = new ArrayList<>();
    /** 锚点前已在制的缸段（提供缸的当前色族与占用，重算不移动） */
    private List<EBlock> running = new ArrayList<>();

    public List<EBlock> getRunningBlocks() {
        return running;
    }

    /** 待排布车 */
    @Data
    public static class ECart {
        private Long id;
        private String code;
        private String fabricType;
        private String colorCode;
        private String colorName;
        private BigDecimal meters;
        private BigDecimal weightKg;
        private String customer;
        private String status;
        private LocalDateTime dueTime;
        private boolean rush;
        /** 解析出的色族 id（null = 色族资料缺失） */
        private Long familyId;
        private String familyCode;
        /** 染色工艺时长（分钟） */
        private int processMinutes;
        /** 是否已发布配方（false = 配方资料不完整） */
        private boolean recipeReady;
        private String recipeIssue;
        private Integer reworkTimes;
    }

    /** 染缸 */
    @Data
    public static class EVat {
        private Long id;
        private String code;
        private String machineType;
        private BigDecimal capacityKg;
        private String workshop;
    }

    @Data
    public static class EFamily {
        private Long id;
        private String code;
        private String name;
        /** SAME/LIGHT/DARK/SPECIAL */
        private String depthLevel;
        /** REACTIVE/DISPERSE/ACID/CATIONIC/BRIGHT/NEUTRAL */
        private String dyeClass;
        private String colorHex;
    }

    @Data
    public static class EWashCell {
        private Long fromFamilyId;
        private Long toFamilyId;
        private String switchType;
        private int washMinutes;
        private BigDecimal washCost;
        private String remark;
    }

    @Data
    public static class EFabricMachine {
        private String fabricType;
        private String machineType;
        private boolean preferred;
    }

    /** 不可用/锁定区间（maintenance 或 lock 统一结构） */
    @Data
    public static class EBlock {
        private Long vatId;
        private String vatCode;
        private LocalDateTime start;
        private LocalDateTime end;
        private String reason;
        /** LOCK / MAINTENANCE / RUNNING（在制缸段） */
        private String kind;
        /** LOCK 段绑定的布车（在制段也填） */
        private Long cartId;
        private String cartCode;
        /** 该段布车色族（用于段后清洗接续；running/lock 布车段时有值） */
        private Long familyId;
    }
}
