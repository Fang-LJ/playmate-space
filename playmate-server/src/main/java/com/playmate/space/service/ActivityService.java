package com.playmate.space.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.playmate.space.common.ErrorCode;
import com.playmate.space.common.exception.BusinessException;
import com.playmate.space.common.exception.ForbiddenException;
import com.playmate.space.common.exception.NotFoundException;
import com.playmate.space.common.exception.UnauthorizedException;
import com.playmate.space.common.security.LoginUserContext;
import com.playmate.space.dto.activity.ActivityDetailResponse;
import com.playmate.space.dto.activity.ActivityListItemResponse;
import com.playmate.space.dto.activity.CreateActivityRequest;
import com.playmate.space.dto.activity.CreateActivityResponse;
import com.playmate.space.dto.activity.UpdateActivityRequest;
import com.playmate.space.entity.ActivityEntity;
import com.playmate.space.entity.ActivityMemberEntity;
import com.playmate.space.entity.FileEntity;
import com.playmate.space.mapper.ActivityMapper;
import com.playmate.space.mapper.ActivityMemberMapper;
import com.playmate.space.mapper.FileMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
public class ActivityService {

    private static final String STATUS_PLANNING = "PLANNING";
    private static final String STATUS_ENDED = "ENDED";
    private static final String STATUS_CANCELED = "CANCELED";
    private static final String ROLE_CREATOR = "CREATOR";
    private static final String MEMBER_STATUS_ACTIVE = "ACTIVE";
    private static final String JOIN_SOURCE_CREATE = "CREATE";
    private static final String FILE_TYPE_ACTIVITY_COVER = "ACTIVITY_COVER";
    private static final String FILE_STATUS_NORMAL = "NORMAL";
    private static final int SHARE_CODE_LENGTH = 12;
    private static final int SHARE_CODE_RETRY_TIMES = 5;
    private static final char[] SHARE_CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Set<String> SUPPORTED_ACTIVITY_TYPES = Set.of(
            "TRAVEL",
            "MEAL",
            "TEAM_BUILDING",
            "BIRTHDAY",
            "CAMPING",
            "DRIVE",
            "BOARD_GAME",
            "OTHER"
    );

    private final ActivityMapper activityMapper;
    private final ActivityMemberMapper activityMemberMapper;
    private final FileMapper fileMapper;

    public ActivityService(
            ActivityMapper activityMapper,
            ActivityMemberMapper activityMemberMapper,
            FileMapper fileMapper
    ) {
        this.activityMapper = activityMapper;
        this.activityMemberMapper = activityMemberMapper;
        this.fileMapper = fileMapper;
    }

    @Transactional
    public CreateActivityResponse createActivity(CreateActivityRequest request) {
        Long userId = requireLoginUserId();
        validateCreateRequest(request);

        FileEntity coverFile = null;
        if (request.getCoverFileId() != null) {
            coverFile = validateCoverFile(request.getCoverFileId(), userId);
        }

        LocalDateTime now = LocalDateTime.now();
        ActivityEntity activity = new ActivityEntity();
        activity.setActivityName(request.getName().trim());
        activity.setActivityType(request.getType().trim());
        activity.setShareCode(generateUniqueShareCode());
        activity.setCoverFileId(coverFile == null ? null : coverFile.getId());
        activity.setCoverUrl(coverFile == null ? null : coverFile.getUrl());
        activity.setLocationName(trimToNull(request.getLocationName()));
        activity.setAddress(trimToNull(request.getAddress()));
        activity.setStartDate(request.getStartDate());
        activity.setEndDate(request.getEndDate());
        activity.setDescription(trimToNull(request.getDescription()));
        activity.setCreatorUserId(userId);
        activity.setStatus(STATUS_PLANNING);
        activity.setMemberCount(1);
        activity.setCreateTime(now);
        activity.setUpdateTime(now);
        activity.setDeleteFlag(0);
        activityMapper.insert(activity);

        ActivityMemberEntity creatorMember = new ActivityMemberEntity();
        creatorMember.setActivityId(activity.getId());
        creatorMember.setUserId(userId);
        creatorMember.setRole(ROLE_CREATOR);
        creatorMember.setMemberStatus(MEMBER_STATUS_ACTIVE);
        creatorMember.setJoinSource(JOIN_SOURCE_CREATE);
        creatorMember.setJoinTime(now);
        creatorMember.setCreateTime(now);
        creatorMember.setUpdateTime(now);
        creatorMember.setDeleteFlag(0);
        activityMemberMapper.insert(creatorMember);

        CreateActivityResponse response = new CreateActivityResponse();
        response.setActivityId(activity.getId());
        response.setShareCode(activity.getShareCode());
        return response;
    }

    public List<ActivityListItemResponse> listMyActivities() {
        return activityMapper.selectMyActivities(requireLoginUserId());
    }

    public ActivityDetailResponse getActivityDetail(Long activityId) {
        Long userId = requireLoginUserId();
        ActivityEntity activity = getExistingActivity(activityId);
        ActivityMemberEntity member = requireActiveMember(activityId, userId);

        return buildDetailResponse(activity, member);
    }

    @Transactional
    public ActivityDetailResponse updateActivity(Long activityId, UpdateActivityRequest request) {
        Long userId = requireLoginUserId();
        ActivityEntity activity = getExistingActivity(activityId);
        ActivityMemberEntity member = requireCreatorMember(activityId, userId, activity);

        if (STATUS_CANCELED.equals(activity.getStatus())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.code(), "已取消活动不允许编辑");
        }

        FileEntity coverFile = null;
        if (request.getCoverFileId() != null) {
            coverFile = validateCoverFile(request.getCoverFileId(), userId);
        }

        if (STATUS_ENDED.equals(activity.getStatus())) {
            validateEndedUpdateRequest(request);
            applyEndedUpdate(activity, request, coverFile);
        } else if (STATUS_PLANNING.equals(activity.getStatus())) {
            applyPlanningUpdate(activity, request, coverFile);
        } else {
            applyPlanningUpdate(activity, request, coverFile);
        }

        activity.setUpdateTime(LocalDateTime.now());
        activityMapper.updateById(activity);
        return buildDetailResponse(activity, member);
    }

    @Transactional
    public ActivityDetailResponse endActivity(Long activityId) {
        Long userId = requireLoginUserId();
        ActivityEntity activity = getExistingActivity(activityId);
        ActivityMemberEntity member = requireCreatorMember(activityId, userId, activity);

        if (STATUS_CANCELED.equals(activity.getStatus())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.code(), "已取消活动不能结束");
        }
        if (STATUS_ENDED.equals(activity.getStatus())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.code(), "活动已结束，不能重复结束");
        }

        activity.setStatus(STATUS_ENDED);
        activity.setUpdateTime(LocalDateTime.now());
        activityMapper.updateById(activity);
        return buildDetailResponse(activity, member);
    }

    @Transactional
    public ActivityDetailResponse cancelActivity(Long activityId) {
        Long userId = requireLoginUserId();
        ActivityEntity activity = getExistingActivity(activityId);
        ActivityMemberEntity member = requireCreatorMember(activityId, userId, activity);

        if (STATUS_ENDED.equals(activity.getStatus())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.code(), "已结束活动不允许取消");
        }
        if (STATUS_CANCELED.equals(activity.getStatus())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.code(), "活动已取消，不能重复取消");
        }

        activity.setStatus(STATUS_CANCELED);
        activity.setUpdateTime(LocalDateTime.now());
        activityMapper.updateById(activity);
        return buildDetailResponse(activity, member);
    }

    private Long requireLoginUserId() {
        Long userId = LoginUserContext.getUserId();
        if (userId == null) {
            throw new UnauthorizedException();
        }
        return userId;
    }

    private void validateCreateRequest(CreateActivityRequest request) {
        if (request.getStartDate().isAfter(request.getEndDate())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.code(), "开始日期不能晚于结束日期");
        }
        String type = request.getType().trim();
        if (!SUPPORTED_ACTIVITY_TYPES.contains(type)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.code(), "不支持的活动类型");
        }
    }

    private ActivityEntity getExistingActivity(Long activityId) {
        if (activityId == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.code(), "activityId 不能为空");
        }

        ActivityEntity activity = activityMapper.selectById(activityId);
        if (activity == null) {
            throw new NotFoundException("活动不存在");
        }
        return activity;
    }

    private ActivityMemberEntity requireActiveMember(Long activityId, Long userId) {
        ActivityMemberEntity member = activityMemberMapper.selectOne(new LambdaQueryWrapper<ActivityMemberEntity>()
                .eq(ActivityMemberEntity::getActivityId, activityId)
                .eq(ActivityMemberEntity::getUserId, userId)
                .last("LIMIT 1"));
        if (member == null || !MEMBER_STATUS_ACTIVE.equals(member.getMemberStatus())) {
            throw new ForbiddenException("无权访问该活动");
        }
        return member;
    }

    private ActivityMemberEntity requireCreatorMember(Long activityId, Long userId, ActivityEntity activity) {
        ActivityMemberEntity member = requireActiveMember(activityId, userId);
        if (!ROLE_CREATOR.equals(member.getRole()) || !userId.equals(activity.getCreatorUserId())) {
            throw new ForbiddenException("只有活动创建者可以操作");
        }
        return member;
    }

    private void validateEndedUpdateRequest(UpdateActivityRequest request) {
        if (request.getName() != null
                || request.getType() != null
                || request.getLocationName() != null
                || request.getAddress() != null
                || request.getStartDate() != null
                || request.getEndDate() != null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.code(), "已结束活动只允许修改封面和描述");
        }
    }

    private void applyEndedUpdate(ActivityEntity activity, UpdateActivityRequest request, FileEntity coverFile) {
        if (request.getCoverFileId() != null) {
            activity.setCoverFileId(coverFile.getId());
            activity.setCoverUrl(coverFile.getUrl());
        }
        if (request.getDescription() != null) {
            activity.setDescription(trimToNull(request.getDescription()));
        }
    }

    private void applyPlanningUpdate(ActivityEntity activity, UpdateActivityRequest request, FileEntity coverFile) {
        if (request.getName() != null) {
            if (!StringUtils.hasText(request.getName())) {
                throw new BusinessException(ErrorCode.PARAM_ERROR.code(), "活动名称不能为空");
            }
            activity.setActivityName(request.getName().trim());
        }
        if (request.getType() != null) {
            String type = request.getType().trim();
            if (!SUPPORTED_ACTIVITY_TYPES.contains(type)) {
                throw new BusinessException(ErrorCode.PARAM_ERROR.code(), "不支持的活动类型");
            }
            activity.setActivityType(type);
        }
        if (request.getCoverFileId() != null) {
            activity.setCoverFileId(coverFile.getId());
            activity.setCoverUrl(coverFile.getUrl());
        }
        if (request.getLocationName() != null) {
            activity.setLocationName(trimToNull(request.getLocationName()));
        }
        if (request.getAddress() != null) {
            activity.setAddress(trimToNull(request.getAddress()));
        }
        if (request.getStartDate() != null) {
            activity.setStartDate(request.getStartDate());
        }
        if (request.getEndDate() != null) {
            activity.setEndDate(request.getEndDate());
        }
        if (request.getDescription() != null) {
            activity.setDescription(trimToNull(request.getDescription()));
        }
        if (activity.getStartDate() != null && activity.getEndDate() != null
                && activity.getStartDate().isAfter(activity.getEndDate())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.code(), "开始日期不能晚于结束日期");
        }
    }

    private FileEntity validateCoverFile(Long coverFileId, Long userId) {
        FileEntity file = fileMapper.selectById(coverFileId);
        if (file == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.code(), "活动封面文件不存在");
        }
        if (!FILE_TYPE_ACTIVITY_COVER.equals(file.getFileType())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.code(), "文件类型不能作为活动封面");
        }
        if (!FILE_STATUS_NORMAL.equals(file.getStatus())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.code(), "文件状态不可用");
        }
        if (!userId.equals(file.getUploadUserId())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.code(), "不能使用其他用户上传的文件作为活动封面");
        }
        return file;
    }

    private String generateUniqueShareCode() {
        for (int i = 0; i < SHARE_CODE_RETRY_TIMES; i++) {
            String shareCode = generateShareCode();
            Long count = activityMapper.countByShareCodeIncludeDeleted(shareCode);
            if (count == 0) {
                return shareCode;
            }
        }
        throw new BusinessException("生成活动分享码失败，请重试");
    }

    private String generateShareCode() {
        StringBuilder builder = new StringBuilder(SHARE_CODE_LENGTH);
        for (int i = 0; i < SHARE_CODE_LENGTH; i++) {
            builder.append(SHARE_CODE_CHARS[SECURE_RANDOM.nextInt(SHARE_CODE_CHARS.length)]);
        }
        return builder.toString();
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private ActivityDetailResponse buildDetailResponse(ActivityEntity activity, ActivityMemberEntity member) {
        ActivityDetailResponse response = new ActivityDetailResponse();
        response.setActivityId(activity.getId());
        response.setName(activity.getActivityName());
        response.setType(activity.getActivityType());
        response.setStatus(activity.getStatus());
        response.setCoverFileId(activity.getCoverFileId());
        response.setCoverUrl(activity.getCoverUrl());
        response.setLocationName(activity.getLocationName());
        response.setAddress(activity.getAddress());
        response.setStartDate(activity.getStartDate());
        response.setEndDate(activity.getEndDate());
        response.setDescription(activity.getDescription());
        response.setCreatorUserId(activity.getCreatorUserId());
        response.setCurrentUserRole(member.getRole());
        response.setMemberCount(activity.getMemberCount());
        response.setShareCode(activity.getShareCode());
        response.setCreateTime(activity.getCreateTime());
        response.setUpdateTime(activity.getUpdateTime());
        return response;
    }
}
