package com.playmate.space.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("t_activity_expense")
public class ActivityExpenseEntity {
    @TableId(type = IdType.AUTO) private Long id;
    private Long activityId; private String title; private String category; private BigDecimal amount; private Long payerUserId;
    private String splitMode; private LocalDateTime expenseTime; private Long receiptFileId; private String description; private Long createdBy;
    private String status; private Long voidedBy; private LocalDateTime voidedAt; private String voidReason; private Integer version;
    private LocalDateTime createTime; private LocalDateTime updateTime;
    @TableLogic(value = "0", delval = "1") private Integer deleteFlag;
    public Long getId(){return id;} public void setId(Long v){id=v;} public Long getActivityId(){return activityId;} public void setActivityId(Long v){activityId=v;}
    public String getTitle(){return title;} public void setTitle(String v){title=v;} public String getCategory(){return category;} public void setCategory(String v){category=v;}
    public BigDecimal getAmount(){return amount;} public void setAmount(BigDecimal v){amount=v;} public Long getPayerUserId(){return payerUserId;} public void setPayerUserId(Long v){payerUserId=v;}
    public String getSplitMode(){return splitMode;} public void setSplitMode(String v){splitMode=v;} public LocalDateTime getExpenseTime(){return expenseTime;} public void setExpenseTime(LocalDateTime v){expenseTime=v;}
    public Long getReceiptFileId(){return receiptFileId;} public void setReceiptFileId(Long v){receiptFileId=v;} public String getDescription(){return description;} public void setDescription(String v){description=v;}
    public Long getCreatedBy(){return createdBy;} public void setCreatedBy(Long v){createdBy=v;} public String getStatus(){return status;} public void setStatus(String v){status=v;}
    public Long getVoidedBy(){return voidedBy;} public void setVoidedBy(Long v){voidedBy=v;} public LocalDateTime getVoidedAt(){return voidedAt;} public void setVoidedAt(LocalDateTime v){voidedAt=v;}
    public String getVoidReason(){return voidReason;} public void setVoidReason(String v){voidReason=v;} public Integer getVersion(){return version;} public void setVersion(Integer v){version=v;}
    public LocalDateTime getCreateTime(){return createTime;} public void setCreateTime(LocalDateTime v){createTime=v;} public LocalDateTime getUpdateTime(){return updateTime;} public void setUpdateTime(LocalDateTime v){updateTime=v;}
    public Integer getDeleteFlag(){return deleteFlag;} public void setDeleteFlag(Integer v){deleteFlag=v;}
}
