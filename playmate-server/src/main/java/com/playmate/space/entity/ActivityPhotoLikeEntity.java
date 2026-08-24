package com.playmate.space.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("t_activity_photo_like")
public class ActivityPhotoLikeEntity {
    @TableId(type = IdType.AUTO) private Long id;
    private Long activityId; private Long photoId; private Long userId; private String status;
    private LocalDateTime likedAt; private LocalDateTime canceledAt; private LocalDateTime createTime; private LocalDateTime updateTime;
    @TableLogic(value = "0", delval = "1") private Integer deleteFlag;
    public Long getId(){return id;} public void setId(Long v){id=v;} public Long getActivityId(){return activityId;} public void setActivityId(Long v){activityId=v;}
    public Long getPhotoId(){return photoId;} public void setPhotoId(Long v){photoId=v;} public Long getUserId(){return userId;} public void setUserId(Long v){userId=v;}
    public String getStatus(){return status;} public void setStatus(String v){status=v;} public LocalDateTime getLikedAt(){return likedAt;} public void setLikedAt(LocalDateTime v){likedAt=v;}
    public LocalDateTime getCanceledAt(){return canceledAt;} public void setCanceledAt(LocalDateTime v){canceledAt=v;} public LocalDateTime getCreateTime(){return createTime;} public void setCreateTime(LocalDateTime v){createTime=v;}
    public LocalDateTime getUpdateTime(){return updateTime;} public void setUpdateTime(LocalDateTime v){updateTime=v;} public Integer getDeleteFlag(){return deleteFlag;} public void setDeleteFlag(Integer v){deleteFlag=v;}
}
