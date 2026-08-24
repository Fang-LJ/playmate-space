package com.playmate.space.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.playmate.space.common.ErrorCode;
import com.playmate.space.common.exception.BusinessException;
import com.playmate.space.common.exception.ForbiddenException;
import com.playmate.space.common.exception.NotFoundException;
import com.playmate.space.dto.photo.*;
import com.playmate.space.entity.*;
import com.playmate.space.mapper.*;
import com.playmate.space.service.photo.PhotoProperties;
import com.playmate.space.storage.FileStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ActivityPhotoService {
    private static final Logger log = LoggerFactory.getLogger(ActivityPhotoService.class);
    private final ActivityCollaborationAccess access; private final ActivityPhotoMapper photoMapper; private final ActivityPhotoLikeMapper likeMapper;
    private final ActivityPhotoReportMapper reportMapper; private final ActivityPhotoAuditTaskMapper taskMapper; private final FileMapper fileMapper;
    private final ActivityMemberDisplayService memberDisplayService; private final FileStorageService storage; private final PhotoProperties properties;
    public ActivityPhotoService(ActivityCollaborationAccess access, ActivityPhotoMapper photoMapper, ActivityPhotoLikeMapper likeMapper,
                                ActivityPhotoReportMapper reportMapper, ActivityPhotoAuditTaskMapper taskMapper, FileMapper fileMapper,
                                ActivityMemberDisplayService memberDisplayService, FileStorageService storage, PhotoProperties properties) {
        this.access=access; this.photoMapper=photoMapper; this.likeMapper=likeMapper; this.reportMapper=reportMapper; this.taskMapper=taskMapper;
        this.fileMapper=fileMapper; this.memberDisplayService=memberDisplayService; this.storage=storage; this.properties=properties;
    }

    public PhotoSummaryResponse summary(Long activityId) {
        Long userId = access.requireUserId(); access.requireActivity(activityId); access.requireActiveMember(activityId, userId);
        long visible = photoMapper.selectCount(photoQuery(activityId).eq(ActivityPhotoEntity::getAuditStatus, "APPROVED").eq(ActivityPhotoEntity::getVisibilityStatus, "NORMAL"));
        long pending = photoMapper.selectCount(photoQuery(activityId).eq(ActivityPhotoEntity::getUploadedBy, userId)
                .and(q -> q.eq(ActivityPhotoEntity::getAuditStatus, "PENDING").or().eq(ActivityPhotoEntity::getVisibilityStatus, "REVIEWING")));
        long mine = photoMapper.selectCount(photoQuery(activityId).eq(ActivityPhotoEntity::getUploadedBy, userId));
        List<ActivityPhotoEntity> uploaded = photoMapper.selectList(photoQuery(activityId).eq(ActivityPhotoEntity::getUploadedBy, userId)
                .eq(ActivityPhotoEntity::getAuditStatus, "APPROVED").eq(ActivityPhotoEntity::getVisibilityStatus, "NORMAL"));
        long received = uploaded.stream().map(ActivityPhotoEntity::getLikeCount).filter(Objects::nonNull).mapToLong(Integer::longValue).sum();
        return new PhotoSummaryResponse(visible, pending, mine, received);
    }

    public PhotoPageResponse list(Long activityId, String scope, String sort, Integer page, Integer pageSize) {
        Long userId = access.requireUserId(); ActivityEntity activity = access.requireActivity(activityId); ActivityMemberEntity member = access.requireActiveMember(activityId, userId);
        String resolvedScope = value(scope, "ALL", Set.of("ALL", "MINE", "LIKED"), "scope 不支持");
        String resolvedSort = value(sort, "LATEST", Set.of("LATEST", "EARLIEST", "MOST_LIKED"), "sort 不支持");
        long current = page == null ? 1 : page, size = pageSize == null ? 30 : pageSize;
        if (current < 1 || size < 1 || size > 60) throw param("分页参数不合法");
        LambdaQueryWrapper<ActivityPhotoEntity> query = photoQuery(activityId);
        if ("ALL".equals(resolvedScope)) query.eq(ActivityPhotoEntity::getAuditStatus, "APPROVED").eq(ActivityPhotoEntity::getVisibilityStatus, "NORMAL");
        if ("MINE".equals(resolvedScope)) query.eq(ActivityPhotoEntity::getUploadedBy, userId);
        if ("LIKED".equals(resolvedScope)) {
            query.eq(ActivityPhotoEntity::getAuditStatus, "APPROVED").eq(ActivityPhotoEntity::getVisibilityStatus, "NORMAL")
                    .inSql(ActivityPhotoEntity::getId, "SELECT photo_id FROM t_activity_photo_like WHERE user_id = " + userId + " AND status = 'ACTIVE' AND delete_flag = 0");
        }
        if ("EARLIEST".equals(resolvedSort)) query.orderByAsc(ActivityPhotoEntity::getCreateTime, ActivityPhotoEntity::getId);
        else if ("MOST_LIKED".equals(resolvedSort)) query.orderByDesc(ActivityPhotoEntity::getLikeCount, ActivityPhotoEntity::getId);
        else query.orderByDesc(ActivityPhotoEntity::getCreateTime, ActivityPhotoEntity::getId);
        Page<ActivityPhotoEntity> result = photoMapper.selectPage(new Page<>(current, size), query);
        return new PhotoPageResponse(toListItems(activity, member, userId, result.getRecords()), current, size, result.getTotal());
    }

    public PhotoDetailResponse detail(Long activityId, Long photoId) {
        Long userId = access.requireUserId(); ActivityEntity activity = access.requireActivity(activityId); ActivityMemberEntity member = access.requireActiveMember(activityId, userId);
        ActivityPhotoEntity photo = requireReadable(activityId, photoId, userId);
        FileEntity file = requireFile(photo.getFileId()); ParticipantProfile profile = profile(activityId, photo.getUploadedBy());
        boolean publicPhoto = normal(photo);
        return new PhotoDetailResponse(photo.getId(), activityId, url(file, file.getPreviewObjectKey(), "preview"), url(file, file.getThumbObjectKey(), "thumbnail"),
                photo.getUploadedBy(), profile.displayName(), profile.avatarUrl(), photo.getTakenAt(), photo.getCreateTime(), file.getWidth(), file.getHeight(),
                file.getSize(), file.getContentType(), safeCount(photo), liked(photo.getId(), userId), photo.getAuditStatus(), photo.getVisibilityStatus(),
                canDelete(activity, userId, photo), publicPhoto || reviewing(photo));
    }

    @Transactional
    public List<CreatePhotoResultResponse> create(Long activityId, CreatePhotosRequest request) {
        Long userId = access.requireUserId(); ActivityEntity activity = access.requireActivity(activityId); access.requireActiveMember(activityId, userId);
        requirePhotoWritable(activity); if (request.fileIds().stream().distinct().count() != request.fileIds().size()) throw param("fileIds 不能重复");
        LocalDateTime now = LocalDateTime.now(); List<CreatePhotoResultResponse> results = new ArrayList<>();
        for (Long fileId : request.fileIds().stream().sorted().toList()) {
            FileEntity file = fileMapper.selectByIdForUpdate(fileId); validateBindable(file, userId);
            ActivityPhotoEntity photo = new ActivityPhotoEntity(); photo.setActivityId(activityId); photo.setFileId(fileId); photo.setUploadedBy(userId);
            photo.setSortNo(0); photo.setStatus("ACTIVE"); photo.setAuditStatus("PENDING"); photo.setVisibilityStatus("HIDDEN"); photo.setLikeCount(0); photo.setVersion(0);
            photo.setCreateTime(now); photo.setUpdateTime(now); photo.setDeleteFlag(0); photoMapper.insert(photo);
            ActivityPhotoAuditTaskEntity task = new ActivityPhotoAuditTaskEntity(); task.setActivityId(activityId); task.setPhotoId(photo.getId()); task.setScene("INITIAL");
            task.setProvider(properties.getModerationProvider().trim().toUpperCase()); task.setStatus("PENDING"); task.setRetryCount(0); task.setCreateTime(now); task.setUpdateTime(now); task.setDeleteFlag(0); taskMapper.insert(task);
            photo.setLatestAuditTaskId(task.getId()); photoMapper.updateById(photo);
            file.setLifecycleStatus("BOUND"); file.setRelatedType("ACTIVITY_PHOTO"); file.setRelatedId(photo.getId()); file.setBoundAt(now); file.setExpireAt(null); file.setUpdateTime(now); fileMapper.updateById(file);
            results.add(new CreatePhotoResultResponse(photo.getId(), fileId, photo.getAuditStatus()));
        }
        return results;
    }

    @Transactional
    public void delete(Long activityId, Long photoId) {
        Long userId = access.requireUserId(); ActivityEntity activity = access.requireActivity(activityId); access.requireActiveMember(activityId, userId);
        ActivityPhotoEntity photo = requirePhotoForUpdate(activityId, photoId); if (!canDelete(activity, userId, photo)) throw new ForbiddenException("无权删除该照片");
        LocalDateTime now = LocalDateTime.now(); photo.setStatus("DELETED"); photo.setDeletedBy(userId); photo.setDeletedAt(now); photo.setVersion(incrementVersion(photo)); photo.setUpdateTime(now); photoMapper.updateById(photo);
        if (photo.getLatestAuditTaskId() != null) { ActivityPhotoAuditTaskEntity task = taskMapper.selectByIdForUpdate(photo.getLatestAuditTaskId()); if (task != null && !terminalTask(task.getStatus())) { task.setStatus("CANCELED"); task.setUpdateTime(now); taskMapper.updateById(task); } }
        FileEntity file = fileMapper.selectByIdForUpdate(photo.getFileId()); if (file != null && "BOUND".equals(file.getLifecycleStatus())) { file.setLifecycleStatus("DELETING"); file.setUpdateTime(now); fileMapper.updateById(file); }
    }

    @Transactional
    public void like(Long activityId, Long photoId) { changeLike(activityId, photoId, true); }
    @Transactional
    public void unlike(Long activityId, Long photoId) { changeLike(activityId, photoId, false); }
    private void changeLike(Long activityId, Long photoId, boolean desiredActive) {
        Long userId = access.requireUserId(); access.requireActivity(activityId); access.requireActiveMember(activityId, userId);
        ActivityPhotoEntity photo = requirePhotoForUpdate(activityId, photoId); if (!normal(photo)) throw new ForbiddenException("该照片当前不可点赞");
        ActivityPhotoLikeEntity relation = likeMapper.selectOne(new LambdaQueryWrapper<ActivityPhotoLikeEntity>().eq(ActivityPhotoLikeEntity::getPhotoId, photoId).eq(ActivityPhotoLikeEntity::getUserId, userId).last("LIMIT 1"));
        LocalDateTime now = LocalDateTime.now();
        if (desiredActive && relation == null) {
            relation = new ActivityPhotoLikeEntity(); relation.setActivityId(activityId); relation.setPhotoId(photoId); relation.setUserId(userId); relation.setStatus("ACTIVE"); relation.setLikedAt(now); relation.setCreateTime(now); relation.setUpdateTime(now); relation.setDeleteFlag(0);
            try { likeMapper.insert(relation); photoMapper.incrementLikeCount(photoId, now); } catch (DuplicateKeyException ignored) { /* concurrent request is idempotent */ }
        } else if (desiredActive && "CANCELED".equals(relation.getStatus())) { relation.setStatus("ACTIVE"); relation.setLikedAt(now); relation.setUpdateTime(now); likeMapper.updateById(relation); photoMapper.incrementLikeCount(photoId, now); }
        else if (!desiredActive && relation != null && "ACTIVE".equals(relation.getStatus())) { relation.setStatus("CANCELED"); relation.setCanceledAt(now); relation.setUpdateTime(now); likeMapper.updateById(relation); photoMapper.decrementLikeCount(photoId, now); }
    }

    @Transactional
    public void report(Long activityId, Long photoId, PhotoReportRequest request) {
        Long userId = access.requireUserId(); ActivityEntity activity = access.requireActivity(activityId); access.requireActiveMember(activityId, userId);
        if (!Set.of("SEXUAL","VIOLENCE","ILLEGAL","PRIVACY","OTHER").contains(request.reasonCode().trim().toUpperCase())) throw param("举报原因不支持");
        ActivityPhotoEntity photo = requirePhotoForUpdate(activityId, photoId);
        boolean reviewing = "ACTIVE".equals(photo.getStatus()) && "APPROVED".equals(photo.getAuditStatus()) && "REVIEWING".equals(photo.getVisibilityStatus());
        if (!normal(photo) && !reviewing) throw new ForbiddenException("该照片当前不可举报");
        ActivityPhotoReportEntity existing = reportMapper.selectOne(new LambdaQueryWrapper<ActivityPhotoReportEntity>().eq(ActivityPhotoReportEntity::getPhotoId, photoId).eq(ActivityPhotoReportEntity::getReporterUserId, userId).last("LIMIT 1"));
        if (existing != null) return;
        LocalDateTime now = LocalDateTime.now();
        ActivityPhotoReportEntity report = new ActivityPhotoReportEntity(); report.setActivityId(activityId); report.setPhotoId(photoId); report.setReporterUserId(userId);
        report.setReasonCode(request.reasonCode().trim().toUpperCase()); report.setStatus("PENDING"); report.setCreateTime(now); report.setUpdateTime(now); report.setDeleteFlag(0);
        if ("REVIEWING".equals(photo.getVisibilityStatus()) && photo.getLatestAuditTaskId() != null) {
            report.setAuditTaskId(photo.getLatestAuditTaskId());
            reportMapper.insert(report);
            return;
        }
        reportMapper.insert(report);
        ActivityPhotoAuditTaskEntity task = new ActivityPhotoAuditTaskEntity(); task.setActivityId(activityId); task.setPhotoId(photoId); task.setReportId(report.getId()); task.setScene("REPORT_RECHECK"); task.setProvider(properties.getModerationProvider().trim().toUpperCase()); task.setStatus("PENDING"); task.setRetryCount(0); task.setCreateTime(now); task.setUpdateTime(now); task.setDeleteFlag(0); taskMapper.insert(task);
        report.setAuditTaskId(task.getId()); reportMapper.updateById(report); photo.setVisibilityStatus("REVIEWING"); photo.setLatestAuditTaskId(task.getId()); photo.setVersion(incrementVersion(photo)); photo.setUpdateTime(now); photoMapper.updateById(photo);
    }

    public PhotoOriginalUrlResponse originalUrl(Long activityId, Long photoId) {
        Long userId = access.requireUserId(); access.requireActivity(activityId); access.requireActiveMember(activityId, userId);
        ActivityPhotoEntity photo = requireReadable(activityId, photoId, userId); FileEntity file = requireFile(photo.getFileId()); LocalDateTime expiresAt = LocalDateTime.now().plus(properties.getPresignedTtl());
        return new PhotoOriginalUrlResponse(url(file, file.getObjectKey(), "original"), expiresAt);
    }

    private List<PhotoListItemResponse> toListItems(ActivityEntity activity, ActivityMemberEntity member, Long userId, List<ActivityPhotoEntity> photos) {
        if (photos.isEmpty()) return List.of(); Set<Long> photoIds = photos.stream().map(ActivityPhotoEntity::getId).collect(Collectors.toSet()); Set<Long> likedIds = new HashSet<>(likeMapper.selectActivePhotoIds(userId, photoIds));
        Map<Long, FileEntity> files = fileMapper.selectByIds(photos.stream().map(ActivityPhotoEntity::getFileId).toList()).stream().collect(Collectors.toMap(FileEntity::getId, item -> item));
        Map<Long, ParticipantProfile> profiles = memberDisplayService.loadParticipantProfiles(activity.getId(), photos.stream().map(ActivityPhotoEntity::getUploadedBy).toList());
        return photos.stream().map(photo -> { FileEntity file = files.get(photo.getFileId()); ParticipantProfile profile = profiles.get(photo.getUploadedBy());
            return new PhotoListItemResponse(photo.getId(), file == null ? null : url(file, file.getThumbObjectKey(), "thumbnail"), file == null ? null : url(file, file.getPreviewObjectKey(), "preview"), photo.getUploadedBy(), profile == null ? "玩伴用户" : profile.displayName(), profile == null ? null : profile.avatarUrl(), safeCount(photo), likedIds.contains(photo.getId()), photo.getAuditStatus(), photo.getVisibilityStatus(), canDelete(activity, userId, photo), photo.getCreateTime()); }).toList();
    }
    private ActivityPhotoEntity requireReadable(Long activityId, Long photoId, Long userId) { ActivityPhotoEntity photo = requirePhoto(activityId, photoId); if (!normal(photo) && !userId.equals(photo.getUploadedBy())) throw new NotFoundException("照片不存在"); return photo; }
    private ActivityPhotoEntity requirePhoto(Long activityId, Long photoId) { ActivityPhotoEntity photo = photoMapper.selectById(photoId); if (photo == null || !activityId.equals(photo.getActivityId()) || !"ACTIVE".equals(photo.getStatus())) throw new NotFoundException("照片不存在"); return photo; }
    private ActivityPhotoEntity requirePhotoForUpdate(Long activityId, Long photoId) { ActivityPhotoEntity photo = photoMapper.selectByIdForUpdate(activityId, photoId); if (photo == null || !"ACTIVE".equals(photo.getStatus())) throw new NotFoundException("照片不存在"); return photo; }
    private void validateBindable(FileEntity file, Long userId) { if (file == null || !userId.equals(file.getUploadUserId()) || !"PHOTO".equals(file.getFileType()) || !"PRIVATE".equals(file.getAccessLevel()) || !"TEMP".equals(file.getLifecycleStatus()) || file.getRelatedId() != null || !"NORMAL".equals(file.getStatus())) throw param("文件不可用于绑定照片"); }
    private void requirePhotoWritable(ActivityEntity activity) { if ("CANCELED".equals(activity.getStatus())) throw new ForbiddenException("已取消活动不允许上传照片"); }
    private boolean normal(ActivityPhotoEntity photo) { return "ACTIVE".equals(photo.getStatus()) && "APPROVED".equals(photo.getAuditStatus()) && "NORMAL".equals(photo.getVisibilityStatus()); }
    private boolean reviewing(ActivityPhotoEntity photo) { return "ACTIVE".equals(photo.getStatus()) && "APPROVED".equals(photo.getAuditStatus()) && "REVIEWING".equals(photo.getVisibilityStatus()); }
    private int incrementVersion(ActivityPhotoEntity photo) { return photo.getVersion() == null ? 1 : photo.getVersion() + 1; }
    private boolean canDelete(ActivityEntity activity, Long userId, ActivityPhotoEntity photo) { return userId.equals(photo.getUploadedBy()) || userId.equals(activity.getCreatorUserId()); }
    private boolean terminalTask(String status) { return "APPROVED".equals(status) || "REJECTED".equals(status) || "CANCELED".equals(status); }
    private boolean liked(Long photoId, Long userId) { return likeMapper.selectCount(new LambdaQueryWrapper<ActivityPhotoLikeEntity>().eq(ActivityPhotoLikeEntity::getPhotoId, photoId).eq(ActivityPhotoLikeEntity::getUserId, userId).eq(ActivityPhotoLikeEntity::getStatus, "ACTIVE")) > 0; }
    private ParticipantProfile profile(Long activityId, Long userId) { return memberDisplayService.loadParticipantProfiles(activityId, List.of(userId)).getOrDefault(userId, new ParticipantProfile(userId, "玩伴用户", null)); }
    private FileEntity requireFile(Long fileId) { FileEntity file = fileMapper.selectById(fileId); if (file == null) throw new NotFoundException("照片文件不存在"); return file; }
    private String url(FileEntity file, String preferredKey, String type) { String key = preferredKey != null ? preferredKey : (file.getPreviewObjectKey() != null ? file.getPreviewObjectKey() : file.getObjectKey()); if (preferredKey == null) log.warn("Photo {} fallback to another private object: fileId={}", type, file.getId()); return storage.generatePresignedGetUrl(file.getBucketName(), key, properties.getPresignedTtl()); }
    private int safeCount(ActivityPhotoEntity photo) { return photo.getLikeCount() == null ? 0 : photo.getLikeCount(); }
    private LambdaQueryWrapper<ActivityPhotoEntity> photoQuery(Long activityId) { return new LambdaQueryWrapper<ActivityPhotoEntity>().eq(ActivityPhotoEntity::getActivityId, activityId).eq(ActivityPhotoEntity::getStatus, "ACTIVE"); }
    private String value(String raw, String fallback, Set<String> options, String message) { String result = raw == null || raw.isBlank() ? fallback : raw.trim().toUpperCase(); if (!options.contains(result)) throw param(message); return result; }
    private BusinessException param(String message) { return new BusinessException(ErrorCode.PARAM_ERROR.code(), message); }
}
