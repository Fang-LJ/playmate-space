package com.playmate.space.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("t_activity_photo")
public class ActivityPhotoEntity {
    @TableId(type = IdType.AUTO) private Long id;
    private Long activityId; private Long fileId; private Long uploadedBy; private LocalDateTime takenAt;
    private String description; private Integer sortNo; private String status; private String auditStatus;
    private String visibilityStatus; private Long latestAuditTaskId; private Integer likeCount;
    private Long deletedBy; private LocalDateTime deletedAt; private Integer version;
    private LocalDateTime createTime; private LocalDateTime updateTime;
    @TableLogic(value = "0", delval = "1") private Integer deleteFlag;
    public Long getId(){return id;} public void setId(Long v){id=v;} public Long getActivityId(){return activityId;} public void setActivityId(Long v){activityId=v;}
    public Long getFileId(){return fileId;} public void setFileId(Long v){fileId=v;} public Long getUploadedBy(){return uploadedBy;} public void setUploadedBy(Long v){uploadedBy=v;}
    public LocalDateTime getTakenAt(){return takenAt;} public void setTakenAt(LocalDateTime v){takenAt=v;} public String getDescription(){return description;} public void setDescription(String v){description=v;}
    public Integer getSortNo(){return sortNo;} public void setSortNo(Integer v){sortNo=v;} public String getStatus(){return status;} public void setStatus(String v){status=v;}
    public String getAuditStatus(){return auditStatus;} public void setAuditStatus(String v){auditStatus=v;} public String getVisibilityStatus(){return visibilityStatus;} public void setVisibilityStatus(String v){visibilityStatus=v;}
    public Long getLatestAuditTaskId(){return latestAuditTaskId;} public void setLatestAuditTaskId(Long v){latestAuditTaskId=v;} public Integer getLikeCount(){return likeCount;} public void setLikeCount(Integer v){likeCount=v;}
    public Long getDeletedBy(){return deletedBy;} public void setDeletedBy(Long v){deletedBy=v;} public LocalDateTime getDeletedAt(){return deletedAt;} public void setDeletedAt(LocalDateTime v){deletedAt=v;}
    public Integer getVersion(){return version;} public void setVersion(Integer v){version=v;} public LocalDateTime getCreateTime(){return createTime;} public void setCreateTime(LocalDateTime v){createTime=v;}
    public LocalDateTime getUpdateTime(){return updateTime;} public void setUpdateTime(LocalDateTime v){updateTime=v;} public Integer getDeleteFlag(){return deleteFlag;} public void setDeleteFlag(Integer v){deleteFlag=v;}
}
