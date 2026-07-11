package com.playmate.space.entity;
import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
@TableName("t_activity_poll_vote")
public class ActivityPollVoteEntity {
 @TableId(type=IdType.AUTO) private Long id; private Long pollId; private Long optionId; private Long userId; private LocalDateTime createTime; private LocalDateTime updateTime; @TableLogic(value="0",delval="1") private Integer deleteFlag;
 public Long getId(){return id;} public void setId(Long v){id=v;} public Long getPollId(){return pollId;} public void setPollId(Long v){pollId=v;} public Long getOptionId(){return optionId;} public void setOptionId(Long v){optionId=v;} public Long getUserId(){return userId;} public void setUserId(Long v){userId=v;} public LocalDateTime getCreateTime(){return createTime;} public void setCreateTime(LocalDateTime v){createTime=v;} public LocalDateTime getUpdateTime(){return updateTime;} public void setUpdateTime(LocalDateTime v){updateTime=v;} public Integer getDeleteFlag(){return deleteFlag;} public void setDeleteFlag(Integer v){deleteFlag=v;}
}
