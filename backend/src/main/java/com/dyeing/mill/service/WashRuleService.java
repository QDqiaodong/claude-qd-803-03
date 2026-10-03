package com.dyeing.mill.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dyeing.mill.common.BizException;
import com.dyeing.mill.domain.ColorFamily;
import com.dyeing.mill.domain.WashRule;
import com.dyeing.mill.domain.WashRuleVersion;
import com.dyeing.mill.dto.WashRuleCellInput;
import com.dyeing.mill.dto.WashRulePublishRequest;
import com.dyeing.mill.repository.ColorFamilyRepository;
import com.dyeing.mill.repository.WashRuleRepository;
import com.dyeing.mill.repository.WashRuleVersionRepository;

/**
 * 清洗规则版本：矩阵整版发布。旧版本永久保留（ARCHIVED），旧方案按其 ruleVersionId 复算。
 */
@Service
public class WashRuleService {

    private final WashRuleVersionRepository versionRepository;
    private final WashRuleRepository ruleRepository;
    private final ColorFamilyRepository familyRepository;

    public WashRuleService(WashRuleVersionRepository versionRepository,
                           WashRuleRepository ruleRepository,
                           ColorFamilyRepository familyRepository) {
        this.versionRepository = versionRepository;
        this.ruleRepository = ruleRepository;
        this.familyRepository = familyRepository;
    }

    @Transactional(readOnly = true)
    public List<WashRuleVersion> listVersions() {
        List<WashRuleVersion> versions = versionRepository.findAllOrdered();
        versions.forEach(v -> v.setRules(ruleRepository.findByVersionIdOrderByIdAsc(v.getId())));
        return versions;
    }

    @Transactional(readOnly = true)
    public WashRuleVersion activeVersion() {
        WashRuleVersion v = versionRepository.findActive()
                .orElseThrow(() -> new BizException("没有已发布的清洗规则版本，请先发布清洗矩阵"));
        v.setRules(ruleRepository.findByVersionIdOrderByIdAsc(v.getId()));
        return v;
    }

    @Transactional(readOnly = true)
    public WashRuleVersion getVersion(Long id) {
        WashRuleVersion v = versionRepository.findById(id)
                .orElseThrow(() -> new BizException("清洗规则版本不存在：id=" + id));
        v.setRules(ruleRepository.findByVersionIdOrderByIdAsc(v.getId()));
        return v;
    }

    /** 颜色族 + 当前矩阵（维护页）。 */
    @Transactional(readOnly = true)
    public Map<String, Object> matrixView(Long versionId) {
        List<ColorFamily> families = familyRepository.findAllOrdered();
        WashRuleVersion version = versionId == null ? activeVersion() : getVersion(versionId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("families", families);
        data.put("version", versionView(version));
        data.put("cells", version.getRules());
        return data;
    }

    /**
     * 发布新版本：复制当前矩阵 + 应用提交的整矩阵单元，旧 ACTIVE 立即转 ARCHIVED。
     */
    @Transactional
    public WashRuleVersion publish(WashRulePublishRequest request) {
        List<ColorFamily> families = familyRepository.findAllOrdered();
        Map<Long, ColorFamily> familyById = new LinkedHashMap<>();
        families.forEach(f -> familyById.put(f.getId(), f));

        List<WashRuleCellInput> cells = request.getCells() == null ? List.of() : request.getCells();
        Set<String> seen = new HashSet<>();
        for (WashRuleCellInput cell : cells) {
            validateCell(cell, familyById, seen);
        }

        WashRuleVersion previous = versionRepository.findActive().orElse(null);
        if (previous != null) {
            previous.setStatus("ARCHIVED");
            versionRepository.save(previous);
        }

        WashRuleVersion version = new WashRuleVersion();
        version.setVersionNo(nextVersionNo());
        version.setStatus("ACTIVE");
        version.setChangeNote(request.getChangeNote());
        version.setCreatedBy(normalize(request.getCreatedBy(), "工艺员"));
        LocalDateTime now = LocalDateTime.now();
        version.setCreateTime(now);
        version.setPublishTime(now);
        WashRuleVersion saved = versionRepository.save(version);

        List<WashRule> rules = new ArrayList<>();
        for (WashRuleCellInput cell : cells) {
            WashRule r = new WashRule();
            r.setVersionId(saved.getId());
            r.setFromFamilyId(cell.getFromFamilyId());
            r.setToFamilyId(cell.getToFamilyId());
            r.setSwitchType(cell.getSwitchType());
            r.setWashMinutes(cell.getWashMinutes());
            r.setWashCost(cell.getWashCost());
            r.setRemark(cell.getRemark());
            rules.add(r);
        }
        ruleRepository.saveAll(rules);
        saved.setRules(rules);
        return saved;
    }

    private void validateCell(WashRuleCellInput cell, Map<Long, ColorFamily> familyById, Set<String> seen) {
        if (cell.getFromFamilyId() == null || cell.getToFamilyId() == null) {
            throw new BizException("清洗矩阵单元必须同时选择源颜色族和目标颜色族");
        }
        if (!familyById.containsKey(cell.getFromFamilyId()) || !familyById.containsKey(cell.getToFamilyId())) {
            throw new BizException("清洗矩阵引用了不存在的颜色族");
        }
        String key = cell.getFromFamilyId() + ">" + cell.getToFamilyId();
        if (!seen.add(key)) {
            throw new BizException("清洗矩阵存在重复单元：" + familyById.get(cell.getFromFamilyId()).getFamilyCode()
                    + " → " + familyById.get(cell.getToFamilyId()).getFamilyCode());
        }
        String type = cell.getSwitchType();
        if (type == null || !List.of("SAME", "L2D", "D2L", "SPECIAL").contains(type)) {
            throw new BizException("切换类型必须是 SAME/L2D/D2L/SPECIAL 之一");
        }
        if (cell.getWashMinutes() == null || cell.getWashMinutes() < 0 || cell.getWashMinutes() > 600) {
            throw new BizException("清洗时长必须在 0~600 分钟之间");
        }
        if (cell.getWashCost() == null || cell.getWashCost().compareTo(BigDecimal.ZERO) < 0) {
            throw new BizException("清洗成本不能为负");
        }
    }

    private int nextVersionNo() {
        return versionRepository.findAllOrdered().stream()
                .map(WashRuleVersion::getVersionNo)
                .max(Comparator.naturalOrder())
                .orElse(0) + 1;
    }

    /** 版本视图（不带 rules，列表用）。 */
    public Map<String, Object> versionView(WashRuleVersion v) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", v.getId());
        map.put("versionNo", v.getVersionNo());
        map.put("status", v.getStatus());
        map.put("changeNote", v.getChangeNote());
        map.put("createdBy", v.getCreatedBy());
        map.put("createTime", v.getCreateTime());
        map.put("publishTime", v.getPublishTime());
        map.put("cellCount", v.getRules().size());
        return map;
    }

    private String normalize(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String t = value.trim();
        return t.length() > 32 ? t.substring(0, 32) : t;
    }
}
