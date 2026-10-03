package com.dyeing.mill.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dyeing.mill.common.BizException;
import com.dyeing.mill.domain.ClothCart;
import com.dyeing.mill.domain.DyeVat;
import com.dyeing.mill.domain.MaintenanceWindow;
import com.dyeing.mill.domain.ScheduleLock;
import com.dyeing.mill.dto.CartSchedulePatchRequest;
import com.dyeing.mill.dto.MaintenanceWindowRequest;
import com.dyeing.mill.dto.ScheduleLockRequest;
import com.dyeing.mill.repository.ClothCartRepository;
import com.dyeing.mill.repository.DyeVatRepository;
import com.dyeing.mill.repository.MaintenanceWindowRepository;
import com.dyeing.mill.repository.ScheduleLockRepository;

/**
 * 排程台基础数据维护：
 *  - 布车交期/急单：只改排程字段，严禁改布车状态、排缸结果（不触碰开缸执行单）；
 *  - 维护时段、人工锁定段的增删查；锁定段互斥校验（同一缸时段不能重叠锁定）。
 */
@Service
public class ScheduleMasterDataService {

    private final ClothCartRepository cartRepository;
    private final DyeVatRepository vatRepository;
    private final MaintenanceWindowRepository maintenanceRepository;
    private final ScheduleLockRepository lockRepository;

    public ScheduleMasterDataService(ClothCartRepository cartRepository,
                                     DyeVatRepository vatRepository,
                                     MaintenanceWindowRepository maintenanceRepository,
                                     ScheduleLockRepository lockRepository) {
        this.cartRepository = cartRepository;
        this.vatRepository = vatRepository;
        this.maintenanceRepository = maintenanceRepository;
        this.lockRepository = lockRepository;
    }

    // ---------------- 布车交期 / 急单（仅排程字段） ----------------

    @Transactional
    public ClothCart patchCartSchedule(Long cartId, CartSchedulePatchRequest request) {
        ClothCart cart = cartRepository.findByIdForUpdate(cartId)
                .orElseThrow(() -> new BizException("布车不存在或已报废：id=" + cartId));
        if (request.getDueTime() != null) {
            if (request.getDueTime().isBlank()) {
                cart.setDueTime(null);
            } else {
                cart.setDueTime(parseTime(request.getDueTime(), "交期"));
            }
        }
        if (request.getRushFlag() != null) {
            int flag = request.getRushFlag();
            if (flag != 0 && flag != 1) {
                throw new BizException("急单标记只能是 0 或 1");
            }
            cart.setRushFlag(flag);
        }
        return cartRepository.save(cart);
    }

    // ---------------- 维护时段 ----------------

    @Transactional(readOnly = true)
    public List<MaintenanceWindow> listMaintenance() {
        return maintenanceRepository.findAllOrdered();
    }

    @Transactional
    public MaintenanceWindow createMaintenance(MaintenanceWindowRequest request) {
        DyeVat vat = requireVat(request.getVatId());
        LocalDateTime start = parseTime(request.getStartTime(), "维护开始时间");
        LocalDateTime end = parseTime(request.getEndTime(), "维护结束时间");
        if (!end.isAfter(start)) {
            throw new BizException("维护结束时间必须晚于开始时间");
        }
        if (request.getReason() == null || request.getReason().isBlank()) {
            throw new BizException("维护原因不能为空");
        }
        MaintenanceWindow w = new MaintenanceWindow();
        w.setVatId(vat.getId());
        w.setVatCode(vat.getVatCode());
        w.setStartTime(start);
        w.setEndTime(end);
        w.setReason(request.getReason().trim());
        w.setCreatedBy(normalize(request.getCreatedBy(), "设备科"));
        w.setCreateTime(LocalDateTime.now());
        return maintenanceRepository.save(w);
    }

    @Transactional
    public void deleteMaintenance(Long id) {
        MaintenanceWindow w = maintenanceRepository.findById(id)
                .orElseThrow(() -> new BizException("维护时段不存在：id=" + id));
        w.setDeleted(1);
        maintenanceRepository.save(w);
    }

    // ---------------- 人工锁定段 ----------------

    @Transactional(readOnly = true)
    public List<ScheduleLock> listLocks() {
        return lockRepository.findAllOrdered();
    }

    @Transactional
    public ScheduleLock createLock(ScheduleLockRequest request) {
        DyeVat vat = requireVat(request.getVatId());
        LocalDateTime start = parseTime(request.getStartTime(), "锁定开始时间");
        LocalDateTime end = parseTime(request.getEndTime(), "锁定结束时间");
        if (!end.isAfter(start)) {
            throw new BizException("锁定结束时间必须晚于开始时间");
        }
        if (request.getLockReason() == null || request.getLockReason().isBlank()) {
            throw new BizException("锁定原因（承诺依据）不能为空");
        }
        // 同缸与其他锁定段互斥
        List<ScheduleLock> overlap = lockRepository.findOverlap(vat.getId(), start, end);
        if (!overlap.isEmpty()) {
            ScheduleLock c = overlap.get(0);
            throw new BizException("染缸 " + vat.getVatCode() + " 在该时段已存在锁定段 "
                    + c.getStartTime() + " ~ " + c.getEndTime() + "（" + c.getLockReason() + "）");
        }
        String cartCode = null;
        if (request.getCartId() != null) {
            ClothCart cart = cartRepository.findById(request.getCartId())
                    .orElseThrow(() -> new BizException("锁定的布车不存在或已报废：id=" + request.getCartId()));
            cartCode = cart.getCartCode();
        }
        ScheduleLock lock = new ScheduleLock();
        lock.setVatId(vat.getId());
        lock.setVatCode(vat.getVatCode());
        lock.setCartId(request.getCartId());
        lock.setCartCode(cartCode);
        lock.setStartTime(start);
        lock.setEndTime(end);
        lock.setLockReason(request.getLockReason().trim());
        lock.setCreatedBy(normalize(request.getCreatedBy(), "调度员"));
        lock.setCreateTime(LocalDateTime.now());
        return lockRepository.save(lock);
    }

    @Transactional
    public void deleteLock(Long id) {
        ScheduleLock lock = lockRepository.findById(id)
                .orElseThrow(() -> new BizException("锁定段不存在：id=" + id));
        lock.setDeleted(1);
        lockRepository.save(lock);
    }

    private DyeVat requireVat(Long vatId) {
        if (vatId == null) {
            throw new BizException("请选择染缸");
        }
        return vatRepository.findById(vatId)
                .orElseThrow(() -> new BizException("染缸不存在：id=" + vatId));
    }

    private LocalDateTime parseTime(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new BizException(label + "不能为空");
        }
        try {
            return LocalDateTime.parse(value.replace(" ", "T"));
        } catch (DateTimeParseException e) {
            throw new BizException(label + "格式有误，应为 yyyy-MM-dd HH:mm");
        }
    }

    private String normalize(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String t = value.trim();
        return t.length() > 32 ? t.substring(0, 32) : t;
    }
}
