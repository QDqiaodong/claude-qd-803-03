package com.dyeing.mill.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dyeing.mill.common.BizException;
import com.dyeing.mill.domain.ChemicalStock;
import com.dyeing.mill.domain.IssueRecord;
import com.dyeing.mill.dto.IssueRequest;
import com.dyeing.mill.dto.StockAlertDTO;
import com.dyeing.mill.repository.ChemicalStockRepository;

/**
 * 染料与助剂库存业务：库存预警、领用（不得超过库存）、批次效期。
 */
@Service
public class InventoryService {

    private final ChemicalStockRepository stockRepository;

    public InventoryService(ChemicalStockRepository stockRepository) {
        this.stockRepository = stockRepository;
    }

    public List<ChemicalStock> list() {
        return stockRepository.findAll();
    }

    public ChemicalStock detail(Long id) {
        return stockRepository.findById(id).orElseThrow(() -> new BizException("染化料不存在：id=" + id));
    }

    /**
     * 库存预警查询（低于安全库存）。
     */
    public List<StockAlertDTO> alerts() {
        return stockRepository.findStockAlerts();
    }

    /**
     * 领用：领用量不得超过库存。
     */
    @Transactional
    public ChemicalStock issue(IssueRequest request) {
        Long stockId = request.getStockId();
        if (stockId == null) {
            throw new BizException("请选择要领用的染化料");
        }
        ChemicalStock stock = detail(stockId);
        BigDecimal qty = request.getQtyKg();
        if (qty == null || qty.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException("领用量必须大于 0");
        }
        BigDecimal available = stock.getStockKg() == null ? BigDecimal.ZERO : stock.getStockKg();
        if (qty.compareTo(available) > 0) {
            throw new BizException("领用量 " + qty.setScale(2, RoundingMode.HALF_UP) + "kg 超过 "
                    + stock.getChemName() + " 当前库存 " + available.setScale(2, RoundingMode.HALF_UP)
                    + "kg（缺口 " + qty.subtract(available).setScale(2, RoundingMode.HALF_UP) + "kg），请先补料");
        }
        if (stock.getExpireDate() != null && stock.getExpireDate().isBefore(java.time.LocalDate.now())) {
            throw new BizException(stock.getChemName() + " 批次 " + stock.getBatchNo() + " 已于 "
                    + stock.getExpireDate() + " 过期，禁止领用");
        }

        stock.setStockKg(available.subtract(qty).setScale(2, RoundingMode.HALF_UP));

        IssueRecord record = new IssueRecord();
        record.setStock(stock);
        record.setChemCode(stock.getChemCode());
        record.setChemName(stock.getChemName());
        record.setQtyKg(qty.setScale(2, RoundingMode.HALF_UP));
        record.setOperator(request.getOperator() == null ? "车间领料" : request.getOperator());
        record.setRecipeCode(request.getRecipeCode());
        record.setCartCode(request.getCartCode());
        record.setIssueTime(LocalDateTime.now());
        record.setDeleted(0);
        stock.getIssues().add(record);

        return stockRepository.save(stock);
    }

    /**
     * 全部领用记录（按时间倒序）。
     */
    public List<IssueRecord> issues() {
        List<IssueRecord> all = new ArrayList<>();
        for (ChemicalStock stock : stockRepository.findAll()) {
            all.addAll(stock.getIssues());
        }
        all.sort((a, b) -> {
            LocalDateTime ta = a.getIssueTime() == null ? LocalDateTime.MIN : a.getIssueTime();
            LocalDateTime tb = b.getIssueTime() == null ? LocalDateTime.MIN : b.getIssueTime();
            return tb.compareTo(ta);
        });
        return all;
    }

    /**
     * 库存总览：分类数量、库存总量、预警数。
     */
    public Map<String, Object> overview() {
        List<ChemicalStock> all = stockRepository.findAll();
        BigDecimal totalKg = BigDecimal.ZERO;
        BigDecimal dyeKg = BigDecimal.ZERO;
        BigDecimal auxKg = BigDecimal.ZERO;
        for (ChemicalStock stock : all) {
            BigDecimal kg = stock.getStockKg() == null ? BigDecimal.ZERO : stock.getStockKg();
            totalKg = totalKg.add(kg);
            if ("DYE".equalsIgnoreCase(stock.getCategory())) {
                dyeKg = dyeKg.add(kg);
            } else {
                auxKg = auxKg.add(kg);
            }
        }
        Map<String, Object> data = new HashMap<>();
        data.put("totalKg", totalKg.setScale(2, RoundingMode.HALF_UP));
        data.put("dyeKg", dyeKg.setScale(2, RoundingMode.HALF_UP));
        data.put("auxKg", auxKg.setScale(2, RoundingMode.HALF_UP));
        data.put("alertCount", stockRepository.findStockAlerts().size());
        data.put("expiredCount", stockRepository.findExpired().size());
        return data;
    }
}
