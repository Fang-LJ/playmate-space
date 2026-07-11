package com.playmate.space.entity;
import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
@TableName("t_activity_poll_option")
public class ActivityPollOptionEntity {
 @TableId(type=IdType.AUTO) private Long id; private Long pollId; private String optionText; private String optionDescription; private String resultPayload; private Integer sortNo; private LocalDateTime createTime; private LocalDateTime updateTime; @TableLogic(value="0",delval="1") private Integer deleteFlag;
 public Long getId(){return id;} public void setId(Long v){id=v;} public Long getPollId(){return pollId;} public void setPollId(Long v){pollId=v;} public String getOptionText(){return optionText;} public void setOptionText(String v){optionText=v;} public String getOptionDescription(){return optionDescription;} public void setOptionDescription(String v){optionDescription=v;} public String getResultPayload(){return resultPayload;} public void setResultPayload(String v){resultPayload=v;} public Integer getSortNo(){return sortNo;} public void setSortNo(Integer v){sortNo=v;} public LocalDateTime getCreateTime(){return createTime;} public void setCreateTime(LocalDateTime v){createTime=v;} public LocalDateTime getUpdateTime(){return updateTime;} public void setUpdateTime(LocalDateTime v){updateTime=v;} public Integer getDeleteFlag(){return deleteFlag;} public void setDeleteFlag(Integer v){deleteFlag=v;}
}
