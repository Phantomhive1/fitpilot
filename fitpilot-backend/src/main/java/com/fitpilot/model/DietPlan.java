package com.fitpilot.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * 饮食计划。
 *
 * mode: lazy（懒人模式：只填身体数据，AI 自己算宏量）/ pro（专业模式：宏量已定，AI 只负责排餐）
 * inputJson: 生成时的归一化输入（复现用）
 * planJson:  AI 返回的完整计划 JSON
 * inputHash: 输入参数哈希，用于同参数去重（防止狂点重复产生费用）
 */
@Entity
@Table(name = "diet_plan")
public class DietPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String sessionId;

    /** lazy / pro */
    private String mode;

    @Column(length = 128)
    private String inputHash;

    private String title;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String inputJson;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String planJson;

    /** 此次响应是否复用了缓存/历史结果 */
    @Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
    private boolean cached = false;

    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
    public String getInputHash() { return inputHash; }
    public void setInputHash(String inputHash) { this.inputHash = inputHash; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getInputJson() { return inputJson; }
    public void setInputJson(String inputJson) { this.inputJson = inputJson; }
    public String getPlanJson() { return planJson; }
    public void setPlanJson(String planJson) { this.planJson = planJson; }
    public boolean isCached() { return cached; }
    public void setCached(boolean cached) { this.cached = cached; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
