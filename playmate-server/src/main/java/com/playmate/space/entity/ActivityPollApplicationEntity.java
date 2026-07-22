package com.playmate.space.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("t_activity_poll_application")
public class ActivityPollApplicationEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long activityId;
    private Long pollId;
    private Long targetItineraryId;
    private Long winnerOptionId;
    private String beforeSnapshot;
    private String afterSnapshot;
    private String changedFields;
    private String unchangedFields;
    private Long appliedBy;
    private LocalDateTime appliedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic(value = "0", delval = "1")
    private Integer deleteFlag;

    public Long getId(){return id;} public void setId(Long v){id=v;}
    public Long getActivityId(){return activityId;} public void setActivityId(Long v){activityId=v;}
    public Long getPollId(){return pollId;} public void setPollId(Long v){pollId=v;}
    public Long getTargetItineraryId(){return targetItineraryId;} public void setTargetItineraryId(Long v){targetItineraryId=v;}
    public Long getWinnerOptionId(){return winnerOptionId;} public void setWinnerOptionId(Long v){winnerOptionId=v;}
    public String getBeforeSnapshot(){return beforeSnapshot;} public void setBeforeSnapshot(String v){beforeSnapshot=v;}
    public String getAfterSnapshot(){return afterSnapshot;} public void setAfterSnapshot(String v){afterSnapshot=v;}
    public String getChangedFields(){return changedFields;} public void setChangedFields(String v){changedFields=v;}
    public String getUnchangedFields(){return unchangedFields;} public void setUnchangedFields(String v){unchangedFields=v;}
    public Long getAppliedBy(){return appliedBy;} public void setAppliedBy(Long v){appliedBy=v;}
    public LocalDateTime getAppliedAt(){return appliedAt;} public void setAppliedAt(LocalDateTime v){appliedAt=v;}
    public LocalDateTime getCreateTime(){return createTime;} public void setCreateTime(LocalDateTime v){createTime=v;}
    public LocalDateTime getUpdateTime(){return updateTime;} public void setUpdateTime(LocalDateTime v){updateTime=v;}
    public Integer getDeleteFlag(){return deleteFlag;} public void setDeleteFlag(Integer v){deleteFlag=v;}
}
