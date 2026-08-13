package com.playmate.space.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("t_activity_finance_state")
public class ActivityFinanceStateEntity {
    @TableId(type = IdType.INPUT)
    private Long activityId;
    private Long financeVersion;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public Long getActivityId() { return activityId; }
    public void setActivityId(Long activityId) { this.activityId = activityId; }
    public Long getFinanceVersion() { return financeVersion; }
    public void setFinanceVersion(Long financeVersion) { this.financeVersion = financeVersion; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}
