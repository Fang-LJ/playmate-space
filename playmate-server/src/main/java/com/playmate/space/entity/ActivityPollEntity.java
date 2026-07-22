package com.playmate.space.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("t_activity_poll")
public class ActivityPollEntity {
    @TableId(type = IdType.AUTO) private Long id;
    private Long activityId; private String purpose; private String decisionType; private Long targetItineraryId; private Long generatedItineraryId;
    private String title; private String description; private String voteType; private Integer allowModify; private LocalDateTime deadline; private String status;
    private String resultApplyMode; private String resultApplyStatus; private Long winnerOptionId; private Integer targetItineraryVersion; private String itineraryTemplate; private String decisionScope;
    private Long createdBy; private LocalDateTime closedAt; private LocalDateTime appliedAt; private Integer version; private LocalDateTime createTime; private LocalDateTime updateTime;
    @TableLogic(value = "0", delval = "1") private Integer deleteFlag;
    public Long getId(){return id;} public void setId(Long v){id=v;} public Long getActivityId(){return activityId;} public void setActivityId(Long v){activityId=v;}
    public String getPurpose(){return purpose;} public void setPurpose(String v){purpose=v;} public String getDecisionType(){return decisionType;} public void setDecisionType(String v){decisionType=v;}
    public Long getTargetItineraryId(){return targetItineraryId;} public void setTargetItineraryId(Long v){targetItineraryId=v;} public Long getGeneratedItineraryId(){return generatedItineraryId;} public void setGeneratedItineraryId(Long v){generatedItineraryId=v;}
    public String getTitle(){return title;} public void setTitle(String v){title=v;} public String getDescription(){return description;} public void setDescription(String v){description=v;}
    public String getVoteType(){return voteType;} public void setVoteType(String v){voteType=v;} public Integer getAllowModify(){return allowModify;} public void setAllowModify(Integer v){allowModify=v;}
    public LocalDateTime getDeadline(){return deadline;} public void setDeadline(LocalDateTime v){deadline=v;} public String getStatus(){return status;} public void setStatus(String v){status=v;}
    public String getResultApplyMode(){return resultApplyMode;} public void setResultApplyMode(String v){resultApplyMode=v;} public String getResultApplyStatus(){return resultApplyStatus;} public void setResultApplyStatus(String v){resultApplyStatus=v;}
    public Long getWinnerOptionId(){return winnerOptionId;} public void setWinnerOptionId(Long v){winnerOptionId=v;} public Integer getTargetItineraryVersion(){return targetItineraryVersion;} public void setTargetItineraryVersion(Integer v){targetItineraryVersion=v;}
    public String getItineraryTemplate(){return itineraryTemplate;} public void setItineraryTemplate(String v){itineraryTemplate=v;} public Long getCreatedBy(){return createdBy;} public void setCreatedBy(Long v){createdBy=v;}
    public String getDecisionScope(){return decisionScope;} public void setDecisionScope(String v){decisionScope=v;}
    public LocalDateTime getClosedAt(){return closedAt;} public void setClosedAt(LocalDateTime v){closedAt=v;} public LocalDateTime getAppliedAt(){return appliedAt;} public void setAppliedAt(LocalDateTime v){appliedAt=v;}
    public Integer getVersion(){return version;} public void setVersion(Integer v){version=v;} public LocalDateTime getCreateTime(){return createTime;} public void setCreateTime(LocalDateTime v){createTime=v;}
    public LocalDateTime getUpdateTime(){return updateTime;} public void setUpdateTime(LocalDateTime v){updateTime=v;} public Integer getDeleteFlag(){return deleteFlag;} public void setDeleteFlag(Integer v){deleteFlag=v;}
}
