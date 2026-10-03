package com.fitpilot.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/** 姿势分析记录（视觉模型对上传照片/视频的纠正反馈） */
@Entity
@Table(
    name = "pose_analysis",
    indexes = {
        // 用哈希做持久化去重时，要靠这个索引快速查
        @Index(name = "idx_pose_hash_created", columnList = "content_hash,created_at")
    }
)
public class PoseAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String imageName;

    /** 用户填写的动作名称，如 "深蹲" */
    @Column(length = 200)
    private String movement;

    /** 文件 SHA-256，相同字节的文件哈希相同，用于跨重启去重 */
    @Column(name = "content_hash", length = 64)
    private String contentHash;

    /** image / video */
    @Column(length = 20)
    private String mediaType;

    private Long fileSize;

    /** 归属会话（前端 localStorage UUID），历史记录按会话隔离，可空（老数据/无 header 时） */
    @Column(name = "session_id", length = 80)
    private String sessionId;

    /** 标记是否被去重缓存命中（false 表示真正调过模型）。
     *  columnDefinition 带 DEFAULT FALSE：H2/MySQL 对已有数据的表执行
     *  ALTER TABLE ADD COLUMN ... NOT NULL 时必须提供默认值，否则报
     *  "NULL not allowed for column" —— 旧记录默认 false（真实调用过模型）语义正确 */
    @Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
    private boolean cached = false;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String feedbackJson;

    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getImageName() { return imageName; }
    public void setImageName(String imageName) { this.imageName = imageName; }
    public String getMovement() { return movement; }
    public void setMovement(String movement) { this.movement = movement; }
    public String getContentHash() { return contentHash; }
    public void setContentHash(String contentHash) { this.contentHash = contentHash; }
    public String getMediaType() { return mediaType; }
    public void setMediaType(String mediaType) { this.mediaType = mediaType; }
    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public boolean isCached() { return cached; }
    public void setCached(boolean cached) { this.cached = cached; }
    public String getFeedbackJson() { return feedbackJson; }
    public void setFeedbackJson(String feedbackJson) { this.feedbackJson = feedbackJson; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}