package com.playmate.space.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("t_activity_todo_user")
public class ActivityTodoUserEntity {
    @TableId(type = IdType.AUTO) private Long id;
    private Long todoId; private Long userId; private String status; private LocalDateTime readTime; private LocalDateTime completedTime;
    private String completionReason; private LocalDateTime createTime; private LocalDateTime updateTime;
    @TableLogic(value = "0", delval = "1") private Integer deleteFlag;
    public Long getId(){return id;} public void setId(Long v){id=v;} public Long getTodoId(){return todoId;} public void setTodoId(Long v){todoId=v;}
    public Long getUserId(){return userId;} public void setUserId(Long v){userId=v;} public String getStatus(){return status;} public void setStatus(String v){status=v;}
    public LocalDateTime getReadTime(){return readTime;} public void setReadTime(LocalDateTime v){readTime=v;} public LocalDateTime getCompletedTime(){return completedTime;} public void setCompletedTime(LocalDateTime v){completedTime=v;}
    public String getCompletionReason(){return completionReason;} public void setCompletionReason(String v){completionReason=v;} public LocalDateTime getCreateTime(){return createTime;} public void setCreateTime(LocalDateTime v){createTime=v;}
    public LocalDateTime getUpdateTime(){return updateTime;} public void setUpdateTime(LocalDateTime v){updateTime=v;} public Integer getDeleteFlag(){return deleteFlag;} public void setDeleteFlag(Integer v){deleteFlag=v;}
}
