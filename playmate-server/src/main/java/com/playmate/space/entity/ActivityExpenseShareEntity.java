package com.playmate.space.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("t_activity_expense_share")
public class ActivityExpenseShareEntity {
    @TableId(type = IdType.AUTO) private Long id;
    private Long expenseId; private Long userId; private BigDecimal shareAmount; private LocalDateTime createTime; private LocalDateTime updateTime;
    @TableLogic(value = "0", delval = "1") private Integer deleteFlag;
    public Long getId(){return id;} public void setId(Long v){id=v;} public Long getExpenseId(){return expenseId;} public void setExpenseId(Long v){expenseId=v;}
    public Long getUserId(){return userId;} public void setUserId(Long v){userId=v;} public BigDecimal getShareAmount(){return shareAmount;} public void setShareAmount(BigDecimal v){shareAmount=v;}
    public LocalDateTime getCreateTime(){return createTime;} public void setCreateTime(LocalDateTime v){createTime=v;} public LocalDateTime getUpdateTime(){return updateTime;} public void setUpdateTime(LocalDateTime v){updateTime=v;}
    public Integer getDeleteFlag(){return deleteFlag;} public void setDeleteFlag(Integer v){deleteFlag=v;}
}
