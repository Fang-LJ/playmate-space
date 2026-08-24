package com.playmate.space.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("t_activity_photo_report")
public class ActivityPhotoReportEntity {
    @TableId(type = IdType.AUTO) private Long id;
    private Long activityId; private Long photoId; private Long reporterUserId; private String reasonCode;
    private String status; private Long auditTaskId; private LocalDateTime handledAt; private LocalDateTime createTime; private LocalDateTime updateTime;
    @TableLogic(value = "0", delval = "1") private Integer deleteFlag;
    public Long getId(){return id;} public void setId(Long v){id=v;} public Long getActivityId(){return activityId;} public void setActivityId(Long v){activityId=v;}
    public Long getPhotoId(){return photoId;} public void setPhotoId(Long v){photoId=v;} public Long getReporterUserId(){return reporterUserId;} public void setReporterUserId(Long v){reporterUserId=v;}
    public String getReasonCode(){return reasonCode;} public void setReasonCode(String v){reasonCode=v;} public String getStatus(){return status;} public void setStatus(String v){status=v;}
    public Long getAuditTaskId(){return auditTaskId;} public void setAuditTaskId(Long v){auditTaskId=v;} public LocalDateTime getHandledAt(){return handledAt;} public void setHandledAt(LocalDateTime v){handledAt=v;}
    public LocalDateTime getCreateTime(){return createTime;} public void setCreateTime(LocalDateTime v){createTime=v;} public LocalDateTime getUpdateTime(){return updateTime;} public void setUpdateTime(LocalDateTime v){updateTime=v;}
    public Integer getDeleteFlag(){return deleteFlag;} public void setDeleteFlag(Integer v){deleteFlag=v;}
}
