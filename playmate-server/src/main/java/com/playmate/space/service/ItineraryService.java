package com.playmate.space.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.playmate.space.common.ErrorCode;
import com.playmate.space.common.exception.BusinessException;
import com.playmate.space.common.exception.ForbiddenException;
import com.playmate.space.common.exception.NotFoundException;
import com.playmate.space.dto.itinerary.CreateItineraryRequest;
import com.playmate.space.dto.itinerary.ItineraryDetailResponse;
import com.playmate.space.dto.itinerary.ItineraryResponse;
import com.playmate.space.dto.itinerary.UpdateItineraryRequest;
import com.playmate.space.dto.poll.PollListItemResponse;
import com.playmate.space.entity.ActivityEntity;
import com.playmate.space.entity.ActivityItineraryEntity;
import com.playmate.space.entity.ActivityMemberEntity;
import com.playmate.space.mapper.ActivityItineraryMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

@Service
public class ItineraryService {
    private static final Set<String> TYPES = Set.of("TRANSPORT", "MEAL", "LODGING", "SIGHTSEEING", "ACTIVITY", "OTHER");
    private static final Set<String> CREATION_MODES = Set.of("DIRECT", "WITH_POLL");
    private final ActivityCollaborationAccess access;
    private final ActivityItineraryMapper itineraryMapper;
    private final PollService pollService;
    private final com.playmate.space.mapper.ActivityPollMapper pollMapper;

    public ItineraryService(ActivityCollaborationAccess access, ActivityItineraryMapper itineraryMapper, PollService pollService, com.playmate.space.mapper.ActivityPollMapper pollMapper) {
        this.access = access; this.itineraryMapper = itineraryMapper; this.pollService = pollService; this.pollMapper = pollMapper;
    }

    public List<ItineraryResponse> list(Long activityId) {
        return list(activityId, false);
    }

    public List<ItineraryResponse> list(Long activityId, boolean includeCanceled) {
        Long userId = access.requireUserId(); access.requireActivity(activityId); access.requireActiveMember(activityId, userId);
        return sorted(activityId, includeCanceled).stream().map(this::toResponse).toList();
    }

    public ItineraryDetailResponse detail(Long activityId, Long itineraryId) {
        Long userId = access.requireUserId(); access.requireActivity(activityId); access.requireActiveMember(activityId, userId);
        ActivityItineraryEntity itinerary = requireItinerary(activityId, itineraryId);
        List<PollListItemResponse> polls = pollService.listForItineraryInternal(activityId, itineraryId, userId);
        return new ItineraryDetailResponse(toResponse(itinerary), polls);
    }

    @Transactional
    public ItineraryResponse create(Long activityId, CreateItineraryRequest request) {
        Long userId = access.requireUserId(); ActivityEntity activity = access.requireActivity(activityId); access.requireActiveMember(activityId, userId); access.requireWritableActivity(activity);
        String creationMode = normalized(request.creationMode());
        if (!CREATION_MODES.contains(creationMode)) throw param("不支持的创建方式");
        if ("WITH_POLL".equals(creationMode) && request.poll() == null) throw param("发起投票时投票配置不能为空");
        validateRequest(request.title(), request.itineraryType(), request.itineraryDate(), request.startTime(), request.endTime(), request.allDay());
        LocalDateTime now = LocalDateTime.now();
        ActivityItineraryEntity itinerary = new ActivityItineraryEntity();
        itinerary.setActivityId(activityId); itinerary.setTitle(request.title().trim()); itinerary.setItineraryType(defaultType(request.itineraryType())); itinerary.setItineraryDate(request.itineraryDate());
        itinerary.setAllDay(Boolean.TRUE.equals(request.allDay()) ? 1 : 0); itinerary.setStartTime(Boolean.TRUE.equals(request.allDay()) ? null : request.startTime()); itinerary.setEndTime(Boolean.TRUE.equals(request.allDay()) ? null : request.endTime());
        itinerary.setLocationName(trim(request.locationName())); itinerary.setAddress(trim(request.address())); itinerary.setDescription(trim(request.description()));
        itinerary.setPlanningStatus("WITH_POLL".equals(creationMode) ? "PENDING_DECISION" : "CONFIRMED"); itinerary.setOriginType("MANUAL"); itinerary.setCreatedBy(userId); itinerary.setVersion(0); itinerary.setCreateTime(now); itinerary.setUpdateTime(now); itinerary.setDeleteFlag(0);
        itineraryMapper.insert(itinerary);
        if ("WITH_POLL".equals(creationMode)) pollService.createLinkedItineraryPoll(activity, itinerary, userId, request.poll());
        return toResponse(itinerary);
    }

    @Transactional
    public ItineraryResponse update(Long activityId, Long itineraryId, UpdateItineraryRequest request) {
        Long userId = access.requireUserId(); ActivityEntity activity = access.requireActivity(activityId); ActivityMemberEntity member = access.requireActiveMember(activityId, userId); access.requireWritableActivity(activity);
        ActivityItineraryEntity itinerary = requireItinerary(activityId, itineraryId); requireItineraryManager(activity, member, userId, itinerary);
        if (request.title() != null) { if (!StringUtils.hasText(request.title())) throw param("行程标题不能为空"); itinerary.setTitle(request.title().trim()); }
        if (request.itineraryType() != null) itinerary.setItineraryType(validType(request.itineraryType()));
        if (request.itineraryDate() != null) itinerary.setItineraryDate(request.itineraryDate());
        if (request.allDay() != null) itinerary.setAllDay(request.allDay() ? 1 : 0);
        if (request.startTime() != null || Integer.valueOf(1).equals(itinerary.getAllDay())) itinerary.setStartTime(Integer.valueOf(1).equals(itinerary.getAllDay()) ? null : request.startTime());
        if (request.endTime() != null || Integer.valueOf(1).equals(itinerary.getAllDay())) itinerary.setEndTime(Integer.valueOf(1).equals(itinerary.getAllDay()) ? null : request.endTime());
        if (request.locationName() != null) itinerary.setLocationName(trim(request.locationName())); if (request.address() != null) itinerary.setAddress(trim(request.address())); if (request.description() != null) itinerary.setDescription(trim(request.description()));
        validateTimes(itinerary.getStartTime(), itinerary.getEndTime(), itinerary.getAllDay()); itinerary.setVersion(itinerary.getVersion() + 1); itinerary.setUpdateTime(LocalDateTime.now()); itineraryMapper.updateById(itinerary);
        return toResponse(itinerary);
    }

    @Transactional
    public ItineraryResponse cancel(Long activityId, Long itineraryId) {
        Long userId = access.requireUserId(); ActivityEntity activity = access.requireActivity(activityId); ActivityMemberEntity member = access.requireActiveMember(activityId, userId); access.requireWritableActivity(activity);
        ActivityItineraryEntity itinerary = requireItinerary(activityId, itineraryId); requireItineraryManager(activity, member, userId, itinerary);
        if ("CANCELED".equals(itinerary.getPlanningStatus())) throw param("行程已取消");
        itinerary.setPlanningStatus("CANCELED"); itinerary.setVersion(itinerary.getVersion() + 1); itinerary.setUpdateTime(LocalDateTime.now()); itineraryMapper.updateById(itinerary); return toResponse(itinerary);
    }

    @Transactional
    public ItineraryResponse restore(Long activityId, Long itineraryId) {
        Long userId = access.requireUserId(); ActivityEntity activity = access.requireActivity(activityId); ActivityMemberEntity member = access.requireActiveMember(activityId, userId); access.requireWritableActivity(activity);
        ActivityItineraryEntity itinerary = requireItinerary(activityId, itineraryId); requireItineraryManager(activity, member, userId, itinerary);
        if (!"CANCELED".equals(itinerary.getPlanningStatus())) throw param("仅已取消的行程可以恢复");
        itinerary.setPlanningStatus("CONFIRMED"); itinerary.setVersion(itinerary.getVersion() + 1); itinerary.setUpdateTime(LocalDateTime.now()); itineraryMapper.updateById(itinerary); return toResponse(itinerary);
    }

    @Transactional
    public void delete(Long activityId, Long itineraryId) {
        Long userId = access.requireUserId(); ActivityEntity activity = access.requireActivity(activityId); ActivityMemberEntity member = access.requireActiveMember(activityId, userId); access.requireWritableActivity(activity);
        ActivityItineraryEntity itinerary = requireItinerary(activityId, itineraryId); requireItineraryManager(activity, member, userId, itinerary);
        List<com.playmate.space.entity.ActivityPollEntity> relatedPolls = pollMapper.selectList(new LambdaQueryWrapper<com.playmate.space.entity.ActivityPollEntity>()
                .eq(com.playmate.space.entity.ActivityPollEntity::getActivityId, activityId)
                .and(wrapper -> wrapper.eq(com.playmate.space.entity.ActivityPollEntity::getTargetItineraryId, itineraryId)
                        .or().eq(com.playmate.space.entity.ActivityPollEntity::getGeneratedItineraryId, itineraryId)));
        boolean hasBlockingPoll = relatedPolls.stream().anyMatch(p -> "DRAFT".equals(p.getStatus()) || "ACTIVE".equals(p.getStatus())
                || "PENDING".equals(p.getResultApplyStatus()) || "REVIEW_REQUIRED".equals(p.getResultApplyStatus()));
        if (hasBlockingPoll) throw param("该行程存在未完成的关联投票，请先完成或取消投票");
        LocalDateTime now = LocalDateTime.now();
        for (com.playmate.space.entity.ActivityPollEntity poll : relatedPolls) {
            if (itineraryId.equals(poll.getTargetItineraryId())) poll.setTargetItineraryId(null);
            if (itineraryId.equals(poll.getGeneratedItineraryId())) poll.setGeneratedItineraryId(null);
            poll.setVersion(poll.getVersion() + 1); poll.setUpdateTime(now); pollMapper.updateById(poll);
        }
        if (itineraryMapper.hardDelete(activityId, itineraryId) != 1) throw new NotFoundException("行程不存在");
    }

    public List<ActivityItineraryEntity> sorted(Long activityId) {
        return sorted(activityId, false);
    }
    public List<ActivityItineraryEntity> sorted(Long activityId, boolean includeCanceled) {
        LambdaQueryWrapper<ActivityItineraryEntity> query = new LambdaQueryWrapper<ActivityItineraryEntity>().eq(ActivityItineraryEntity::getActivityId, activityId);
        if (!includeCanceled) query.ne(ActivityItineraryEntity::getPlanningStatus, "CANCELED");
        List<ActivityItineraryEntity> rows = itineraryMapper.selectList(query);
        rows.sort(Comparator.comparing(ActivityItineraryEntity::getItineraryDate).thenComparing(i -> i.getStartTime() == null ? 1 : 0).thenComparing(ActivityItineraryEntity::getStartTime, Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(ActivityItineraryEntity::getId)); return rows;
    }
    public ActivityItineraryEntity requireItinerary(Long activityId, Long itineraryId) {
        ActivityItineraryEntity itinerary = itineraryMapper.selectById(itineraryId);
        if (itinerary == null || !activityId.equals(itinerary.getActivityId())) throw new NotFoundException("行程不存在"); return itinerary;
    }
    public ItineraryResponse toResponse(ActivityItineraryEntity i) { return new ItineraryResponse(i.getId(), i.getActivityId(), i.getTitle(), i.getItineraryType(), i.getItineraryDate(), i.getStartTime(), i.getEndTime(), Integer.valueOf(1).equals(i.getAllDay()), i.getLocationName(), i.getAddress(), i.getDescription(), i.getPlanningStatus(), ItineraryTimeStatusResolver.resolve(i, LocalDateTime.now()), i.getOriginType(), i.getOriginPollId(), i.getCreatedBy(), i.getVersion(), i.getCreateTime(), i.getUpdateTime()); }
    private void requireItineraryManager(ActivityEntity a, ActivityMemberEntity m, Long userId, ActivityItineraryEntity i) { if (!userId.equals(i.getCreatedBy()) && !access.isActivityCreator(a,m,userId)) throw new ForbiddenException("只能管理自己创建的行程"); }
    private void validateRequest(String title,String type,java.time.LocalDate date,java.time.LocalTime start,java.time.LocalTime end,Boolean allDay){ if(!StringUtils.hasText(title)||date==null)throw param("行程标题和日期不能为空"); validType(type); validateTimes(start,end,Boolean.TRUE.equals(allDay)?1:0); }
    private void validateTimes(java.time.LocalTime start,java.time.LocalTime end,Integer allDay){ if(Integer.valueOf(1).equals(allDay))return; if(start!=null&&end!=null&&!end.isAfter(start))throw param("结束时间必须晚于开始时间"); }
    private String validType(String v){String type=defaultType(v);if(!TYPES.contains(type))throw param("不支持的行程类型");return type;} private String defaultType(String v){return StringUtils.hasText(v)?normalized(v):"OTHER";} private String normalized(String v){return v.trim().toUpperCase();} private String trim(String v){return StringUtils.hasText(v)?v.trim():null;} private BusinessException param(String m){return new BusinessException(ErrorCode.PARAM_ERROR.code(),m);}
}
