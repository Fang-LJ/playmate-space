package com.playmate.space.dto.photo;

import java.time.LocalDateTime;
public record PhotoListItemResponse(Long photoId, String thumbnailUrl, String previewUrl, Long uploadedBy, String uploaderNickname,
                                    String uploaderAvatarUrl, Integer likeCount, Boolean likedByMe, String auditStatus,
                                    String visibilityStatus, Boolean canDelete, LocalDateTime createTime) {}
