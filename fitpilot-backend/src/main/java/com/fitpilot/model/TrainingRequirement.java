package com.fitpilot.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/** 训练需求：目标 / 水平 / 器械 / 伤病 等 */
@Entity
@Table(name = "training_requirement")
public class TrainingRequirement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nickname;

    /** 增肌 / 减脂 / 力量 / 体能 / 塑形 等 */
    private String goal;

    /** 初级 / 中级 / 高级 */
    private String level;

    /** 可用器械，多个用顿号分隔 */
    @Column(length = 1000)
    private String equipment;

    /** 伤病或活动受限说明 */
    @Column(length = 1000)
    private String injuries;

    private Integer daysPerWeek;

    private Integer sessionMinutes;

    @Column(length = 2000)
    private String notes;

    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public String getGoal() { return goal; }
    public void setGoal(String goal) { this.goal = goal; }
    public String getLevel() { return level; }
    public void setLevel(String level) { this.level = level; }
    public String getEquipment() { return equipment; }
    public void setEquipment(String equipment) { this.equipment = equipment; }
    public String getInjuries() { return injuries; }
    public void setInjuries(String injuries) { this.injuries = injuries; }
    public Integer getDaysPerWeek() { return daysPerWeek; }
    public void setDaysPerWeek(Integer daysPerWeek) { this.daysPerWeek = daysPerWeek; }
    public Integer getSessionMinutes() { return sessionMinutes; }
    public void setSessionMinutes(Integer sessionMinutes) { this.sessionMinutes = sessionMinutes; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
