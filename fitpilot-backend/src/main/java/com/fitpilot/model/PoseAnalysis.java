package com.fitpilot.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/** 姿势分析记录（视觉模型对上传照片的纠正反馈） */
@Entity
@Table(name = "pose_analysis")
public class PoseAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String imageName;

    /** 用户填写的动作名称，如 "深蹲" */
    @Column(length = 200)
    private String movement;

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
    public String getFeedbackJson() { return feedbackJson; }
    public void setFeedbackJson(String feedbackJson) { this.feedbackJson = feedbackJson; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
