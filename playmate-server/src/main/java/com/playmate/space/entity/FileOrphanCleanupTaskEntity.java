package com.playmate.space.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("t_file_orphan_cleanup_task")
public class FileOrphanCleanupTaskEntity {
    @TableId(type = IdType.AUTO) private Long id;
    private String bucketName; private String objectKey; private String status; private String reason; private Integer retryCount;
    private LocalDateTime nextRetryTime; private String lastError; private LocalDateTime completedAt; private LocalDateTime createTime; private LocalDateTime updateTime; private Integer deleteFlag;
    public Long getId(){return id;} public void setId(Long v){id=v;} public String getBucketName(){return bucketName;} public void setBucketName(String v){bucketName=v;} public String getObjectKey(){return objectKey;} public void setObjectKey(String v){objectKey=v;} public String getStatus(){return status;} public void setStatus(String v){status=v;} public String getReason(){return reason;} public void setReason(String v){reason=v;} public Integer getRetryCount(){return retryCount;} public void setRetryCount(Integer v){retryCount=v;} public LocalDateTime getNextRetryTime(){return nextRetryTime;} public void setNextRetryTime(LocalDateTime v){nextRetryTime=v;} public String getLastError(){return lastError;} public void setLastError(String v){lastError=v;} public LocalDateTime getCompletedAt(){return completedAt;} public void setCompletedAt(LocalDateTime v){completedAt=v;} public LocalDateTime getCreateTime(){return createTime;} public void setCreateTime(LocalDateTime v){createTime=v;} public LocalDateTime getUpdateTime(){return updateTime;} public void setUpdateTime(LocalDateTime v){updateTime=v;} public Integer getDeleteFlag(){return deleteFlag;} public void setDeleteFlag(Integer v){deleteFlag=v;}
}
