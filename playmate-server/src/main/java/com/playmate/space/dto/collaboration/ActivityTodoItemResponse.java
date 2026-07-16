package com.playmate.space.dto.collaboration;

import java.time.LocalDateTime;

/** Current user's persisted pending task. Legacy aliases preserve the existing mini-program contract. */
public class ActivityTodoItemResponse {
    private Long todoId; private Long activityId; private String activityName; private String todoType; private String title;
    private String content; private String actionType; private String sourceType; private Long sourceId; private LocalDateTime dueTime; private String userStatus;
    public Long getTodoId(){return todoId;} public void setTodoId(Long v){todoId=v;} public Long getActivityId(){return activityId;} public void setActivityId(Long v){activityId=v;}
    public String getActivityName(){return activityName;} public void setActivityName(String v){activityName=v;} public String getTodoType(){return todoType;} public void setTodoType(String v){todoType=v;}
    public String getTitle(){return title;} public void setTitle(String v){title=v;} public String getContent(){return content;} public void setContent(String v){content=v;}
    public String getActionType(){return actionType;} public void setActionType(String v){actionType=v;} public String getSourceType(){return sourceType;} public void setSourceType(String v){sourceType=v;}
    public Long getSourceId(){return sourceId;} public void setSourceId(Long v){sourceId=v;} public LocalDateTime getDueTime(){return dueTime;} public void setDueTime(LocalDateTime v){dueTime=v;}
    public String getUserStatus(){return userStatus;} public void setUserStatus(String v){userStatus=v;}
    public String getTargetType(){return sourceType;} public Long getTargetId(){return sourceId;} public String getDescription(){return content;}
    public LocalDateTime getDueAt(){return dueTime;}
    public String getActionText(){return switch(actionType == null ? "" : actionType){
        case "VIEW_POLL" -> "查看投票";
        case "CONFIRM_POLL_RESULT" -> "确认结果";
        case "ACK_REMINDER" -> "我已知晓";
        case "VIEW_SETTLEMENT" -> "查看结算";
        default -> "查看详情";
    };}
}
