package com.dyeing.mill.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.dyeing.mill.common.BizException;
import com.dyeing.mill.engine.ScheduleInput;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * 方案输入快照的序列化/反序列化。
 * 快照保存的是“当时的全量输入”（布车、染缸、色族、适配、维护、锁定、在制段），
 * 清洗矩阵单独按方案的 ruleVersionId 取库 —— 两者结合即可按当时版本完整复算。
 */
@Component
public class ScheduleSnapshots {

    private final ObjectMapper mapper;

    public ScheduleSnapshots() {
        this.mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    public Map<String, Object> fromInput(ScheduleInput in) {
        Snapshot s = new Snapshot();
        s.version = 1;
        s.anchor = in.getAnchor();
        s.horizonEnd = in.getHorizonEnd();

        for (ScheduleInput.EVat v : in.getVats()) {
            JsonVat j = new JsonVat();
            j.id = v.getId();
            j.code = v.getCode();
            j.machineType = v.getMachineType();
            j.capacityKg = v.getCapacityKg();
            j.workshop = v.getWorkshop();
            s.vats.add(j);
        }
        for (ScheduleInput.EFamily f : in.getFamilies()) {
            JsonFamily j = new JsonFamily();
            j.id = f.getId();
            j.code = f.getCode();
            j.name = f.getName();
            j.depthLevel = f.getDepthLevel();
            j.dyeClass = f.getDyeClass();
            j.colorHex = f.getColorHex();
            s.families.add(j);
        }
        for (ScheduleInput.EFabricMachine fm : in.getFabricMachines()) {
            JsonFabricMachine j = new JsonFabricMachine();
            j.fabricType = fm.getFabricType();
            j.machineType = fm.getMachineType();
            j.preferred = fm.isPreferred();
            s.fabricMachines.add(j);
        }
        for (ScheduleInput.ECart c : in.getCarts()) {
            JsonCart j = new JsonCart();
            j.id = c.getId();
            j.code = c.getCode();
            j.fabricType = c.getFabricType();
            j.colorCode = c.getColorCode();
            j.colorName = c.getColorName();
            j.meters = c.getMeters();
            j.weightKg = c.getWeightKg();
            j.customer = c.getCustomer();
            j.status = c.getStatus();
            j.due = c.getDueTime();
            j.rush = c.isRush();
            j.familyId = c.getFamilyId();
            j.familyCode = c.getFamilyCode();
            j.processMinutes = c.getProcessMinutes();
            j.recipeReady = c.isRecipeReady();
            j.recipeIssue = c.getRecipeIssue();
            j.reworkTimes = c.getReworkTimes();
            s.carts.add(j);
        }
        s.maintenance = blocks(in.getMaintenance());
        s.locks = blocks(in.getLocks());
        s.running = blocks(in.getRunningBlocks());
        return mapper.convertValue(s, Map.class);
    }

    private List<JsonBlock> blocks(List<ScheduleInput.EBlock> src) {
        List<JsonBlock> out = new ArrayList<>();
        for (ScheduleInput.EBlock b : src) {
            JsonBlock j = new JsonBlock();
            j.vatId = b.getVatId();
            j.vatCode = b.getVatCode();
            j.start = b.getStart();
            j.end = b.getEnd();
            j.reason = b.getReason();
            j.cartId = b.getCartId();
            j.cartCode = b.getCartCode();
            j.familyId = b.getFamilyId();
            out.add(j);
        }
        return out;
    }

    public String write(Map<String, Object> snapshotMap) {
        try {
            return mapper.writeValueAsString(snapshotMap);
        } catch (JsonProcessingException e) {
            throw new BizException("排程输入快照序列化失败：" + e.getMessage());
        }
    }

    public Snapshot parse(String json) {
        try {
            return mapper.readValue(json, Snapshot.class);
        } catch (JsonProcessingException e) {
            throw new BizException("排程输入快照无法解析，旧方案不能复算：" + e.getMessage());
        }
    }

    // ---- 快照行结构（包级可见，Assembler 直接读） ----

    static class Snapshot {
        public int version = 1;
        public LocalDateTime anchor;
        public LocalDateTime horizonEnd;
        public List<JsonVat> vats = new ArrayList<>();
        public List<JsonFamily> families = new ArrayList<>();
        public List<JsonFabricMachine> fabricMachines = new ArrayList<>();
        public List<JsonCart> carts = new ArrayList<>();
        public List<JsonBlock> maintenance = new ArrayList<>();
        public List<JsonBlock> locks = new ArrayList<>();
        public List<JsonBlock> running = new ArrayList<>();
    }

    static class JsonVat {
        public Long id;
        public String code;
        public String machineType;
        public BigDecimal capacityKg;
        public String workshop;
    }

    static class JsonFamily {
        public Long id;
        public String code;
        public String name;
        public String depthLevel;
        public String dyeClass;
        public String colorHex;
    }

    static class JsonFabricMachine {
        public String fabricType;
        public String machineType;
        public boolean preferred;
    }

    static class JsonCart {
        public Long id;
        public String code;
        public String fabricType;
        public String colorCode;
        public String colorName;
        public BigDecimal meters;
        public BigDecimal weightKg;
        public String customer;
        public String status;
        public LocalDateTime due;
        public boolean rush;
        public Long familyId;
        public String familyCode;
        public int processMinutes;
        public boolean recipeReady;
        public String recipeIssue;
        public Integer reworkTimes;
    }

    static class JsonBlock {
        public Long vatId;
        public String vatCode;
        public LocalDateTime start;
        public LocalDateTime end;
        public String reason;
        public Long cartId;
        public String cartCode;
        public Long familyId;
    }

    /** 供其它服务构造有序 Map（保持 JSON 字段稳定）。 */
    public static Map<String, Object> orderedMap() {
        return new LinkedHashMap<>();
    }
}
