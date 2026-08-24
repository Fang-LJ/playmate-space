package com.playmate.space.dto.photo;

import java.time.LocalDateTime;
public record PhotoDetailResponse(Long photoId, Long activityId, String previewUrl, String thumbnailUrl, Long uploadedBy,
                                  String uploaderNickname, String uploaderAvatarUrl, LocalDateTime takenAt, LocalDateTime uploadTime,
                                  Integer width, Integer height, Long size, String contentType, Integer likeCount, Boolean likedByMe,
                                  String auditStatus, String visibilityStatus, Boolean canDelete, Boolean canReport) {}
