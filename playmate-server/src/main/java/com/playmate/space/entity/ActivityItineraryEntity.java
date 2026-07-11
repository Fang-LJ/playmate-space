package com.playmate.space.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@TableName("t_activity_itinerary")
public class ActivityItineraryEntity {
    @TableId(type = IdType.AUTO) private Long id;
    private Long activityId;
    private String title;
    private String itineraryType;
    private LocalDate itineraryDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private Integer allDay;
    private String locationName;
    private String address;
    private String description;
    private String planningStatus;
    private String originType;
    private Long originPollId;
    private Long createdBy;
    private Integer version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic(value = "0", delval = "1") private Integer deleteFlag;
    public Long getId(){return id;} public void setId(Long v){id=v;} public Long getActivityId(){return activityId;} public void setActivityId(Long v){activityId=v;}
    public String getTitle(){return title;} public void setTitle(String v){title=v;} public String getItineraryType(){return itineraryType;} public void setItineraryType(String v){itineraryType=v;}
    public LocalDate getItineraryDate(){return itineraryDate;} public void setItineraryDate(LocalDate v){itineraryDate=v;} public LocalTime getStartTime(){return startTime;} public void setStartTime(LocalTime v){startTime=v;}
    public LocalTime getEndTime(){return endTime;} public void setEndTime(LocalTime v){endTime=v;} public Integer getAllDay(){return allDay;} public void setAllDay(Integer v){allDay=v;}
    public String getLocationName(){return locationName;} public void setLocationName(String v){locationName=v;} public String getAddress(){return address;} public void setAddress(String v){address=v;}
    public String getDescription(){return description;} public void setDescription(String v){description=v;} public String getPlanningStatus(){return planningStatus;} public void setPlanningStatus(String v){planningStatus=v;}
    public String getOriginType(){return originType;} public void setOriginType(String v){originType=v;} public Long getOriginPollId(){return originPollId;} public void setOriginPollId(Long v){originPollId=v;}
    public Long getCreatedBy(){return createdBy;} public void setCreatedBy(Long v){createdBy=v;} public Integer getVersion(){return version;} public void setVersion(Integer v){version=v;}
    public LocalDateTime getCreateTime(){return createTime;} public void setCreateTime(LocalDateTime v){createTime=v;} public LocalDateTime getUpdateTime(){return updateTime;} public void setUpdateTime(LocalDateTime v){updateTime=v;}
    public Integer getDeleteFlag(){return deleteFlag;} public void setDeleteFlag(Integer v){deleteFlag=v;}
}
