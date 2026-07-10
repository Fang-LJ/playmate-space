package com.playmate.space.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("t_user_identity")
public class UserIdentityEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String identityType;
    private String identifier;
    private String unionid;
    private String appid;
    private String authNickname;
    private String authAvatarUrl;
    private String rawProfileJson;
    private LocalDateTime lastLoginTime;
    private LocalDateTime bindTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic(value = "0", delval = "1")
    private Integer deleteFlag;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getIdentityType() {
        return identityType;
    }

    public void setIdentityType(String identityType) {
        this.identityType = identityType;
    }

    public String getIdentifier() {
        return identifier;
    }

    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    public String getUnionid() {
        return unionid;
    }

    public void setUnionid(String unionid) {
        this.unionid = unionid;
    }

    public String getAppid() {
        return appid;
    }

    public void setAppid(String appid) {
        this.appid = appid;
    }

    public String getAuthNickname() {
        return authNickname;
    }

    public void setAuthNickname(String authNickname) {
        this.authNickname = authNickname;
    }

    public String getAuthAvatarUrl() {
        return authAvatarUrl;
    }

    public void setAuthAvatarUrl(String authAvatarUrl) {
        this.authAvatarUrl = authAvatarUrl;
    }

    public String getRawProfileJson() {
        return rawProfileJson;
    }

    public void setRawProfileJson(String rawProfileJson) {
        this.rawProfileJson = rawProfileJson;
    }

    public LocalDateTime getLastLoginTime() {
        return lastLoginTime;
    }

    public void setLastLoginTime(LocalDateTime lastLoginTime) {
        this.lastLoginTime = lastLoginTime;
    }

    public LocalDateTime getBindTime() {
        return bindTime;
    }

    public void setBindTime(LocalDateTime bindTime) {
        this.bindTime = bindTime;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }

    public Integer getDeleteFlag() {
        return deleteFlag;
    }

    public void setDeleteFlag(Integer deleteFlag) {
        this.deleteFlag = deleteFlag;
    }
}
