package com.playmate.space.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("t_activity_settlement")
public class ActivitySettlementEntity {
    @TableId(type = IdType.AUTO) private Long id;
    private Long activityId; private Long fromUserId; private Long toUserId; private BigDecimal amount; private String status;
    private LocalDateTime completedAt; private Long operatedBy; private String remark; private LocalDateTime canceledAt; private Long canceledBy; private String cancelReason;
    private LocalDateTime createTime; private LocalDateTime updateTime;
    @TableLogic(value = "0", delval = "1") private Integer deleteFlag;
    public Long getId(){return id;} public void setId(Long v){id=v;} public Long getActivityId(){return activityId;} public void setActivityId(Long v){activityId=v;}
    public Long getFromUserId(){return fromUserId;} public void setFromUserId(Long v){fromUserId=v;} public Long getToUserId(){return toUserId;} public void setToUserId(Long v){toUserId=v;}
    public BigDecimal getAmount(){return amount;} public void setAmount(BigDecimal v){amount=v;} public String getStatus(){return status;} public void setStatus(String v){status=v;}
    public LocalDateTime getCompletedAt(){return completedAt;} public void setCompletedAt(LocalDateTime v){completedAt=v;} public Long getOperatedBy(){return operatedBy;} public void setOperatedBy(Long v){operatedBy=v;}
    public String getRemark(){return remark;} public void setRemark(String v){remark=v;} public LocalDateTime getCanceledAt(){return canceledAt;} public void setCanceledAt(LocalDateTime v){canceledAt=v;}
    public Long getCanceledBy(){return canceledBy;} public void setCanceledBy(Long v){canceledBy=v;} public String getCancelReason(){return cancelReason;} public void setCancelReason(String v){cancelReason=v;}
    public LocalDateTime getCreateTime(){return createTime;} public void setCreateTime(LocalDateTime v){createTime=v;} public LocalDateTime getUpdateTime(){return updateTime;} public void setUpdateTime(LocalDateTime v){updateTime=v;}
    public Integer getDeleteFlag(){return deleteFlag;} public void setDeleteFlag(Integer v){deleteFlag=v;}
}
