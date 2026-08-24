package com.playmate.space.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("t_activity_photo_audit_task")
public class ActivityPhotoAuditTaskEntity {
    @TableId(type = IdType.AUTO) private Long id;
    private Long activityId; private Long photoId; private Long reportId; private String scene; private String provider;
    private String providerTraceId; private String status; private Integer retryCount; private LocalDateTime nextRetryTime;
    private String lastError; private String resultCode; private String resultDetail; private LocalDateTime submittedAt; private LocalDateTime completedAt;
    private LocalDateTime createTime; private LocalDateTime updateTime;
    @TableLogic(value = "0", delval = "1") private Integer deleteFlag;
    public Long getId(){return id;} public void setId(Long v){id=v;} public Long getActivityId(){return activityId;} public void setActivityId(Long v){activityId=v;}
    public Long getPhotoId(){return photoId;} public void setPhotoId(Long v){photoId=v;} public Long getReportId(){return reportId;} public void setReportId(Long v){reportId=v;}
    public String getScene(){return scene;} public void setScene(String v){scene=v;} public String getProvider(){return provider;} public void setProvider(String v){provider=v;}
    public String getProviderTraceId(){return providerTraceId;} public void setProviderTraceId(String v){providerTraceId=v;} public String getStatus(){return status;} public void setStatus(String v){status=v;}
    public Integer getRetryCount(){return retryCount;} public void setRetryCount(Integer v){retryCount=v;} public LocalDateTime getNextRetryTime(){return nextRetryTime;} public void setNextRetryTime(LocalDateTime v){nextRetryTime=v;}
    public String getLastError(){return lastError;} public void setLastError(String v){lastError=v;} public String getResultCode(){return resultCode;} public void setResultCode(String v){resultCode=v;}
    public String getResultDetail(){return resultDetail;} public void setResultDetail(String v){resultDetail=v;} public LocalDateTime getSubmittedAt(){return submittedAt;} public void setSubmittedAt(LocalDateTime v){submittedAt=v;}
    public LocalDateTime getCompletedAt(){return completedAt;} public void setCompletedAt(LocalDateTime v){completedAt=v;} public LocalDateTime getCreateTime(){return createTime;} public void setCreateTime(LocalDateTime v){createTime=v;}
    public LocalDateTime getUpdateTime(){return updateTime;} public void setUpdateTime(LocalDateTime v){updateTime=v;} public Integer getDeleteFlag(){return deleteFlag;} public void setDeleteFlag(Integer v){deleteFlag=v;}
}
