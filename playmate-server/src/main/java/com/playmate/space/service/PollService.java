package com.playmate.space.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.playmate.space.common.ErrorCode;
import com.playmate.space.common.exception.BusinessException;
import com.playmate.space.common.exception.ForbiddenException;
import com.playmate.space.common.exception.NotFoundException;
import com.playmate.space.dto.poll.*;
import com.playmate.space.entity.*;
import com.playmate.space.mapper.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class PollService {
    private static final Logger log = LoggerFactory.getLogger(PollService.class);
    private static final Set<String> PURPOSES = Set.of("GENERAL", "UPDATE_ITINERARY", "CREATE_ITINERARY");
    private static final Set<String> DECISIONS = Set.of(
            "PLACE", "TIME", "TRANSPORT", "ROUTE", "CONTENT", "RESTAURANT", "ITINERARY_NAME", "OTHER");
    private static final Set<String> VOTE_TYPES = Set.of("SINGLE", "MULTIPLE");

    private final ActivityCollaborationAccess access;
    private final ActivityPollMapper pollMapper;
    private final ActivityPollOptionMapper optionMapper;
    private final ActivityPollVoteMapper voteMapper;
    private final ActivityItineraryMapper itineraryMapper;
    private final ActivityPollApplicationMapper applicationMapper;
    private final UserMapper userMapper;
    private final ObjectMapper objectMapper;
    private final ActivityTodoLifecycleService todoLifecycleService;
    private final ItineraryFieldPolicy fieldPolicy;
    private final ItineraryTypePolicy typePolicy;

    public PollService(
            ActivityCollaborationAccess access,
            ActivityPollMapper pollMapper,
            ActivityPollOptionMapper optionMapper,
            ActivityPollVoteMapper voteMapper,
            ActivityItineraryMapper itineraryMapper,
            ActivityPollApplicationMapper applicationMapper,
            UserMapper userMapper,
            ObjectMapper objectMapper,
            ActivityTodoLifecycleService todoLifecycleService,
            ItineraryFieldPolicy fieldPolicy,
            ItineraryTypePolicy typePolicy
    ) {
        this.access = access;
        this.pollMapper = pollMapper;
        this.optionMapper = optionMapper;
        this.voteMapper = voteMapper;
        this.itineraryMapper = itineraryMapper;
        this.applicationMapper = applicationMapper;
        this.userMapper = userMapper;
        this.objectMapper = objectMapper;
        this.todoLifecycleService = todoLifecycleService;
        this.fieldPolicy = fieldPolicy;
        this.typePolicy = typePolicy;
    }

    @Transactional
    public List<PollListItemResponse> list(Long activityId) {
        Long userId = access.requireUserId();
        access.requireActivity(activityId);
        access.requireActiveMember(activityId, userId);
        finalizeExpiredByActivity(activityId);
        return listInternal(activityId, userId);
    }

    @Transactional
    public PollDetailResponse detail(Long activityId, Long pollId) {
        Long userId = access.requireUserId();
        access.requireActivity(activityId);
        access.requireActiveMember(activityId, userId);
        finalizeExpiredPollIfNeeded(pollId);
        return detailInternal(activityId, pollId, userId);
    }

    @Transactional
    public PollDetailResponse create(Long activityId, CreatePollRequest request) {
        Long userId = access.requireUserId();
        ActivityEntity activity = access.requireActivity(activityId);
        access.requireActiveMember(activityId, userId);
        access.requireWritableActivity(activity);
        ActivityPollEntity poll = createInternal(activity, userId, request, null, null);
        todoLifecycleService.onPollCreated(poll);
        return detailInternal(activityId, poll.getId(), userId);
    }

    public void createLinkedItineraryPoll(
            ActivityEntity activity,
            ActivityItineraryEntity itinerary,
            Long userId,
            CreatePollRequest request
    ) {
        ActivityPollEntity poll = createInternal(
                activity, userId, request, itinerary.getId(), itinerary.getVersion());
        todoLifecycleService.onPollCreated(poll);
    }

    @Transactional
    public PollDetailResponse update(Long activityId, Long pollId, UpdatePollRequest request) {
        Long userId = access.requireUserId();
        ActivityEntity activity = access.requireActivity(activityId);
        ActivityMemberEntity member = access.requireActiveMember(activityId, userId);
        access.requireWritableActivity(activity);
        ActivityPollEntity poll = requirePoll(activityId, pollId);
        requirePollManager(activity, member, userId, poll);
        if (!"DRAFT".equals(poll.getStatus()) && !"ACTIVE".equals(poll.getStatus())) {
            throw param("当前投票不能编辑");
        }
        if (request.title() != null) {
            if (!StringUtils.hasText(request.title())) throw param("投票标题不能为空");
            poll.setTitle(request.title().trim());
        }
        if (request.description() != null) poll.setDescription(trim(request.description()));
        if (request.deadline() != null) poll.setDeadline(request.deadline());
        if (request.allowModify() != null) poll.setAllowModify(request.allowModify() ? 1 : 0);
        poll.setVersion(poll.getVersion() + 1);
        poll.setUpdateTime(LocalDateTime.now());
        pollMapper.updateById(poll);
        return detailInternal(activityId, pollId, userId);
    }

    @Transactional
    public PollDetailResponse submitVote(Long activityId, Long pollId, VoteRequest request) {
        Long userId = access.requireUserId();
        ActivityEntity activity = access.requireActivity(activityId);
        access.requireActiveMember(activityId, userId);
        access.requireWritableActivity(activity);
        finalizeExpiredPollIfNeeded(pollId);
        ActivityPollEntity poll = requirePoll(activityId, pollId);
        if (!"ACTIVE".equals(poll.getStatus())) throw param("投票已结束，不能继续参与");

        Set<Long> selected = new LinkedHashSet<>(request.optionIds());
        if (selected.size() != request.optionIds().size()) throw param("投票选项不能重复");
        if ("SINGLE".equals(poll.getVoteType()) && selected.size() != 1) {
            throw param("单选投票只能选择一个选项");
        }
        Set<Long> validOptionIds = optionMapper.selectList(
                        new LambdaQueryWrapper<ActivityPollOptionEntity>()
                                .eq(ActivityPollOptionEntity::getPollId, pollId))
                .stream().map(ActivityPollOptionEntity::getId).collect(Collectors.toSet());
        if (!validOptionIds.containsAll(selected)) throw param("包含无效投票选项");

        LocalDateTime now = LocalDateTime.now();
        List<ActivityPollVoteEntity> existing = voteMapper.selectList(
                new LambdaQueryWrapper<ActivityPollVoteEntity>()
                        .eq(ActivityPollVoteEntity::getPollId, pollId)
                        .eq(ActivityPollVoteEntity::getUserId, userId));
        Set<Long> oldOptionIds = existing.stream()
                .map(ActivityPollVoteEntity::getOptionId).collect(Collectors.toSet());
        if ("SINGLE".equals(poll.getVoteType())) {
            voteMapper.removeOtherActiveVotes(pollId, userId, selected.iterator().next(), now);
        }
        for (ActivityPollVoteEntity vote : existing) {
            if (!selected.contains(vote.getOptionId())) {
                vote.setDeleteFlag(1);
                vote.setUpdateTime(now);
                voteMapper.updateById(vote);
            }
        }
        for (Long optionId : selected) {
            if (oldOptionIds.contains(optionId)) continue;
            int restored = voteMapper.restoreVote(pollId, optionId, userId, now);
            if (restored == 0) {
                ActivityPollVoteEntity vote = new ActivityPollVoteEntity();
                vote.setPollId(pollId);
                vote.setOptionId(optionId);
                vote.setUserId(userId);
                vote.setCreateTime(now);
                vote.setUpdateTime(now);
                vote.setDeleteFlag(0);
                voteMapper.insert(vote);
            }
        }
        todoLifecycleService.onUserVoted(poll.getId(), userId);
        return detailInternal(activityId, pollId, userId);
    }

    @Transactional
    public PollDetailResponse close(Long activityId, Long pollId) {
        Long userId = access.requireUserId();
        ActivityEntity activity = access.requireActivity(activityId);
        ActivityMemberEntity member = access.requireActiveMember(activityId, userId);
        access.requireWritableActivity(activity);
        ActivityPollEntity poll = requirePoll(activityId, pollId);
        requirePollManager(activity, member, userId, poll);
        closeAndFinalize(poll, activity);
        return detailInternal(activityId, pollId, userId);
    }

    @Transactional
    public PollDetailResponse cancel(Long activityId, Long pollId) {
        Long userId = access.requireUserId();
        ActivityEntity activity = access.requireActivity(activityId);
        ActivityMemberEntity member = access.requireActiveMember(activityId, userId);
        access.requireWritableActivity(activity);
        ActivityPollEntity poll = requirePoll(activityId, pollId);
        requirePollManager(activity, member, userId, poll);
        if (!"DRAFT".equals(poll.getStatus()) && !"ACTIVE".equals(poll.getStatus())) {
            throw param("当前投票不能取消");
        }
        poll.setStatus("CANCELED");
        poll.setUpdateTime(LocalDateTime.now());
        poll.setVersion(poll.getVersion() + 1);
        pollMapper.updateById(poll);
        todoLifecycleService.cancelPollTodos(poll);
        return detailInternal(activityId, pollId, userId);
    }

    @Transactional(readOnly = true)
    public PollResultPreviewResponse previewResult(Long activityId, Long pollId, Long optionId) {
        Long userId = access.requireUserId();
        access.requireActivity(activityId);
        access.requireActiveMember(activityId, userId);
        ActivityPollEntity poll = requirePoll(activityId, pollId);
        if ("GENERAL".equals(poll.getPurpose())) throw param("普通投票不需要应用结果");
        ActivityPollOptionEntity option = requireOption(pollId, optionId);
        return buildPreview(poll, option);
    }

    @Transactional
    public PollDetailResponse applyResult(
            Long activityId, Long pollId, ApplyPollResultRequest request
    ) {
        Long userId = access.requireUserId();
        ActivityEntity activity = access.requireActivity(activityId);
        ActivityMemberEntity member = access.requireActiveMember(activityId, userId);
        access.requireWritableActivity(activity);
        ActivityPollEntity poll = requirePoll(activityId, pollId);
        if (!"CLOSED".equals(poll.getStatus())) throw param("投票尚未结束");
        if ("APPLIED".equals(poll.getResultApplyStatus())) return detailInternal(activityId, pollId, userId);
        if ("GENERAL".equals(poll.getPurpose())) throw param("普通投票不需要应用结果");
        ActivityPollOptionEntity option = requireOption(pollId, request.optionId());
        if ("UPDATE_ITINERARY".equals(poll.getPurpose())) {
            ActivityItineraryEntity target = requireTarget(poll);
            if (!userId.equals(target.getCreatedBy()) && !access.isActivityCreator(activity, member, userId)) {
                throw new ForbiddenException("只有行程创建者或活动创建者可以确认应用结果");
            }
        } else if (!userId.equals(poll.getCreatedBy()) && !access.isActivityCreator(activity, member, userId)) {
            throw new ForbiddenException("无权确认投票结果");
        }
        poll.setWinnerOptionId(option.getId());
        applyResultInternal(poll, activity, option, true, userId);
        return detailInternal(activityId, pollId, userId);
    }

    public List<PollListItemResponse> listForItineraryInternal(
            Long activityId, Long itineraryId, Long userId
    ) {
        finalizeExpiredByActivity(activityId);
        return listInternal(activityId, userId).stream()
                .filter(poll -> itineraryId.equals(poll.targetItineraryId())
                        || itineraryId.equals(poll.generatedItineraryId()))
                .toList();
    }

    public void finalizeExpiredPollIfNeeded(Long pollId) {
        ActivityPollEntity poll = pollMapper.selectById(pollId);
        if (poll == null || !"ACTIVE".equals(poll.getStatus()) || poll.getDeadline() == null
                || poll.getDeadline().isAfter(LocalDateTime.now())) return;
        ActivityEntity activity = access.requireActivity(poll.getActivityId());
        closeAndFinalize(poll, activity);
    }

    private void finalizeExpiredByActivity(Long activityId) {
        List<ActivityPollEntity> polls = pollMapper.selectList(
                new LambdaQueryWrapper<ActivityPollEntity>()
                        .eq(ActivityPollEntity::getActivityId, activityId)
                        .eq(ActivityPollEntity::getStatus, "ACTIVE")
                        .le(ActivityPollEntity::getDeadline, LocalDateTime.now()));
        for (ActivityPollEntity poll : polls) finalizeExpiredPollIfNeeded(poll.getId());
    }

    private ActivityPollEntity createInternal(
            ActivityEntity activity,
            Long userId,
            CreatePollRequest request,
            Long forcedTargetId,
            Integer forcedVersion
    ) {
        String purpose = upper(request.purpose());
        String voteType = upper(request.voteType());
        String decisionType = upper(request.decisionType());
        if (!PURPOSES.contains(purpose) || !VOTE_TYPES.contains(voteType)
                || !DECISIONS.contains(decisionType)) throw param("投票参数不合法");
        if (!"GENERAL".equals(purpose) && !"SINGLE".equals(voteType)) {
            throw param("关联行程的投票必须使用单选");
        }

        Long targetId = forcedTargetId != null ? forcedTargetId : request.targetItineraryId();
        Integer targetVersion = forcedVersion;
        String itineraryType = null;
        if ("UPDATE_ITINERARY".equals(purpose)) {
            if (targetId == null) throw param("关联已有行程时必须指定目标行程");
            ActivityItineraryEntity target = itineraryMapper.selectById(targetId);
            if (target == null || !activity.getId().equals(target.getActivityId())) {
                throw param("关联行程不存在");
            }
            if (targetVersion == null) targetVersion = target.getVersion();
            itineraryType = typePolicy.normalizeType(target.getItineraryType());
        }
        Map<String, Object> itineraryTemplate = normalizeItineraryTemplate(
                request.itineraryTemplate());
        if ("CREATE_ITINERARY".equals(purpose)
                && itineraryTemplate.isEmpty()) {
            throw param("生成行程投票需要固定行程信息");
        }
        fieldPolicy.validateTemplate(itineraryTemplate);
        if ("CREATE_ITINERARY".equals(purpose)) {
            itineraryType = typePolicy.normalizeType(string(itineraryTemplate, "itineraryType"));
            validateItineraryTemplate(itineraryTemplate, itineraryType);
        }

        List<String> decisionScope = fieldPolicy.normalizeNewScope(
                purpose, itineraryType, decisionType, request.decisionScope());
        for (PollOptionRequest option : request.options()) {
            Map<String, Object> payload = option.resultPayload() == null
                    ? Map.of() : option.resultPayload();
            if (!"GENERAL".equals(purpose)) {
                if (payload.isEmpty()) throw param("关联行程的投票选项必须包含结果字段");
                fieldPolicy.validatePayload(payload, decisionScope);
            }
        }

        LocalDateTime now = LocalDateTime.now();
        ActivityPollEntity poll = new ActivityPollEntity();
        poll.setActivityId(activity.getId());
        poll.setPurpose(purpose);
        poll.setDecisionType(decisionType);
        poll.setTargetItineraryId(targetId);
        poll.setTitle(request.title().trim());
        poll.setDescription(trim(request.description()));
        poll.setVoteType(voteType);
        poll.setAllowModify(Boolean.TRUE.equals(request.allowModify()) ? 1 : 0);
        poll.setDeadline(request.deadline());
        poll.setStatus("ACTIVE");
        poll.setResultApplyMode("GENERAL".equals(purpose) ? "NONE" : "AUTO");
        poll.setResultApplyStatus("GENERAL".equals(purpose) ? "NOT_REQUIRED" : "PENDING");
        poll.setTargetItineraryVersion(targetVersion);
        poll.setItineraryTemplate(jsonObject(itineraryTemplate));
        poll.setDecisionScope(jsonValue(decisionScope));
        poll.setCreatedBy(userId);
        poll.setVersion(0);
        poll.setCreateTime(now);
        poll.setUpdateTime(now);
        poll.setDeleteFlag(0);
        pollMapper.insert(poll);

        int sortNo = 0;
        for (PollOptionRequest optionRequest : request.options()) {
            ActivityPollOptionEntity option = new ActivityPollOptionEntity();
            option.setPollId(poll.getId());
            option.setOptionText(optionRequest.optionText().trim());
            option.setOptionDescription(trim(optionRequest.optionDescription()));
            option.setResultPayload(jsonObject(optionRequest.resultPayload()));
            option.setSortNo(sortNo++);
            option.setCreateTime(now);
            option.setUpdateTime(now);
            option.setDeleteFlag(0);
            optionMapper.insert(option);
        }
        return poll;
    }

    private void closeAndFinalize(ActivityPollEntity poll, ActivityEntity activity) {
        if (!"ACTIVE".equals(poll.getStatus())) return;
        LocalDateTime now = LocalDateTime.now();
        if (pollMapper.closeActivePoll(poll.getId(), now) != 1) return;
        ActivityPollEntity closed = pollMapper.selectById(poll.getId());
        todoLifecycleService.closePollVoteTodos(
                closed, closed.getDeadline() != null && !closed.getDeadline().isAfter(now));

        List<ActivityPollVoteEntity> votes = voteMapper.selectList(
                new LambdaQueryWrapper<ActivityPollVoteEntity>()
                        .eq(ActivityPollVoteEntity::getPollId, poll.getId()));
        Map<Long, Long> counts = votes.stream().collect(
                Collectors.groupingBy(ActivityPollVoteEntity::getOptionId, Collectors.counting()));
        if ("GENERAL".equals(closed.getPurpose())) {
            closed.setResultApplyStatus("NOT_REQUIRED");
            closed.setUpdateTime(now);
            pollMapper.updateById(closed);
            return;
        }
        if (counts.isEmpty()) {
            review(closed, activity);
            return;
        }
        long max = counts.values().stream().max(Long::compareTo).orElse(0L);
        List<Long> winners = counts.entrySet().stream()
                .filter(entry -> entry.getValue() == max)
                .map(Map.Entry::getKey).toList();
        if (winners.size() != 1) {
            review(closed, activity);
            return;
        }
        ActivityPollOptionEntity winner = requireOption(closed.getId(), winners.getFirst());
        closed.setWinnerOptionId(winner.getId());
        applyResultInternal(closed, activity, winner, false, closed.getCreatedBy());
    }

    private void applyResultInternal(
            ActivityPollEntity poll,
            ActivityEntity activity,
            ActivityPollOptionEntity winner,
            boolean manual,
            Long appliedBy
    ) {
        try {
            List<String> scope = decisionScope(poll);
            if (!manual && fieldPolicy.requiresManualReview(scope)) {
                review(poll, activity);
                return;
            }
            if ("UPDATE_ITINERARY".equals(poll.getPurpose())) {
                applyUpdate(poll, activity, winner, manual, appliedBy);
            } else if ("CREATE_ITINERARY".equals(poll.getPurpose())) {
                applyCreate(poll, activity, winner, appliedBy);
            } else {
                poll.setResultApplyStatus("NOT_REQUIRED");
                pollMapper.updateById(poll);
            }
        } catch (BusinessException ex) {
            if (manual) throw ex;
            log.info("automatic poll result requires review: pollId={}, reason={}", poll.getId(), ex.getMessage());
            review(poll, activity);
        }
    }

    private void applyUpdate(
            ActivityPollEntity poll,
            ActivityEntity activity,
            ActivityPollOptionEntity winner,
            boolean manual,
            Long appliedBy
    ) {
        ActivityItineraryEntity target = requireTarget(poll);
        if (!manual && !poll.getCreatedBy().equals(target.getCreatedBy())
                && !poll.getCreatedBy().equals(activity.getCreatorUserId())) {
            review(poll, activity);
            return;
        }
        if (!Objects.equals(target.getVersion(), poll.getTargetItineraryVersion())) {
            review(poll, activity);
            return;
        }

        List<String> scope = decisionScope(poll);
        Map<String, Object> payload = jsonMap(winner.getResultPayload());
        Map<String, Object> before = fieldPolicy.snapshot(target);
        fieldPolicy.apply(target, payload, scope);
        typePolicy.validatePersistedFields(target);
        Map<String, Object> after = fieldPolicy.snapshot(target);
        ItineraryFieldPolicy.ChangeSet changeSet = fieldPolicy.changes(before, after, scope);
        if (changeSet.changedFields().isEmpty()) throw param("胜出方案没有产生可应用的字段变化");

        target.setPlanningStatus("CONFIRMED");
        target.setVersion(target.getVersion() + 1);
        target.setUpdateTime(LocalDateTime.now());
        itineraryMapper.updateById(target);
        saveApplication(poll, target, winner, before, after, changeSet, appliedBy);
        markApplied(poll);
    }

    private void applyCreate(
            ActivityPollEntity poll,
            ActivityEntity activity,
            ActivityPollOptionEntity winner,
            Long appliedBy
    ) {
        ActivityItineraryEntity itinerary = itineraryFromTemplate(
                activity, poll, jsonMap(poll.getItineraryTemplate()));
        Map<String, Object> before = fieldPolicy.snapshot(itinerary);
        List<String> scope = decisionScope(poll);
        fieldPolicy.apply(itinerary, jsonMap(winner.getResultPayload()), scope);
        validateNewItinerary(itinerary);
        Map<String, Object> after = fieldPolicy.snapshot(itinerary);
        ItineraryFieldPolicy.ChangeSet changeSet = fieldPolicy.changes(before, after, scope);
        if (changeSet.changedFields().isEmpty()) throw param("胜出方案没有产生可应用的字段变化");

        itineraryMapper.insert(itinerary);
        poll.setGeneratedItineraryId(itinerary.getId());
        saveApplication(poll, itinerary, winner, before, after, changeSet, appliedBy);
        markApplied(poll);
    }

    private PollResultPreviewResponse buildPreview(
            ActivityPollEntity poll, ActivityPollOptionEntity option
    ) {
        List<String> scope = decisionScope(poll);
        ActivityItineraryEntity candidate;
        Map<String, Object> before;
        if ("UPDATE_ITINERARY".equals(poll.getPurpose())) {
            ActivityItineraryEntity target = requireTarget(poll);
            candidate = fieldPolicy.copy(target);
            before = fieldPolicy.snapshot(target);
        } else if ("CREATE_ITINERARY".equals(poll.getPurpose())) {
            ActivityEntity activity = access.requireActivity(poll.getActivityId());
            candidate = itineraryFromTemplate(activity, poll, jsonMap(poll.getItineraryTemplate()));
            before = fieldPolicy.snapshot(candidate);
        } else {
            throw param("普通投票不需要应用结果");
        }
        fieldPolicy.apply(candidate, jsonMap(option.getResultPayload()), scope);
        typePolicy.validatePersistedFields(candidate);
        Map<String, Object> after = fieldPolicy.snapshot(candidate);
        ItineraryFieldPolicy.ChangeSet changes = fieldPolicy.changes(before, after, scope);
        return new PollResultPreviewResponse(
                poll.getId(), option.getId(), option.getOptionText(),
                candidate.getId() != null ? candidate.getId() : poll.getTargetItineraryId(),
                candidate.getTitle(), changes.changedFields(), changes.unchangedFields(),
                !changes.changedFields().isEmpty());
    }

    private ActivityItineraryEntity itineraryFromTemplate(
            ActivityEntity activity, ActivityPollEntity poll, Map<String, Object> template
    ) {
        ActivityItineraryEntity itinerary = new ActivityItineraryEntity();
        LocalDateTime now = LocalDateTime.now();
        itinerary.setActivityId(activity.getId());
        itinerary.setTitle(string(template, "title"));
        itinerary.setItineraryType(typePolicy.normalizeType(string(template, "itineraryType")));
        itinerary.setItineraryDate(date(template, "itineraryDate"));
        itinerary.setAllDay(booleanValue(template, "allDay") ? 1 : 0);
        itinerary.setStartTime(Integer.valueOf(1).equals(itinerary.getAllDay())
                ? null : time(template, "startTime"));
        itinerary.setEndTime(Integer.valueOf(1).equals(itinerary.getAllDay())
                ? null : time(template, "endTime"));
        itinerary.setTransportMode(string(template, "transportMode"));
        itinerary.setDepartureName(string(template, "departureName"));
        itinerary.setDestinationName(string(template, "destinationName"));
        String legacyRouteDetail = string(template, "routeDetail");
        itinerary.setRouteDetail(legacyRouteDetail);
        itinerary.setMealType(string(template, "mealType"));
        itinerary.setRestaurantName(string(template, "restaurantName"));
        itinerary.setActivityContent(string(template, "activityContent"));
        itinerary.setLocationName(string(template, "locationName"));
        itinerary.setAddress(string(template, "address"));
        itinerary.setDescription(typePolicy.mergeLegacyRouteDetail(
                string(template, "description"), legacyRouteDetail));
        itinerary.setPlanningStatus("CONFIRMED");
        itinerary.setOriginType("POLL_RESULT");
        itinerary.setOriginPollId(poll.getId());
        itinerary.setCreatedBy(poll.getCreatedBy());
        itinerary.setVersion(0);
        itinerary.setCreateTime(now);
        itinerary.setUpdateTime(now);
        itinerary.setDeleteFlag(0);
        return itinerary;
    }

    private void validateNewItinerary(ActivityItineraryEntity itinerary) {
        if (!StringUtils.hasText(itinerary.getTitle()) || itinerary.getItineraryDate() == null) {
            throw param("生成行程需要固定的行程名称和日期");
        }
        typePolicy.validatePersistedFields(itinerary);
    }

    private void saveApplication(
            ActivityPollEntity poll,
            ActivityItineraryEntity itinerary,
            ActivityPollOptionEntity winner,
            Map<String, Object> before,
            Map<String, Object> after,
            ItineraryFieldPolicy.ChangeSet changeSet,
            Long appliedBy
    ) {
        ActivityPollApplicationEntity existing = applicationMapper.selectOne(
                new LambdaQueryWrapper<ActivityPollApplicationEntity>()
                        .eq(ActivityPollApplicationEntity::getPollId, poll.getId()));
        if (existing != null) return;
        LocalDateTime now = LocalDateTime.now();
        ActivityPollApplicationEntity application = new ActivityPollApplicationEntity();
        application.setActivityId(poll.getActivityId());
        application.setPollId(poll.getId());
        application.setTargetItineraryId(itinerary.getId());
        application.setWinnerOptionId(winner.getId());
        application.setBeforeSnapshot(jsonValue(before));
        application.setAfterSnapshot(jsonValue(after));
        application.setChangedFields(jsonValue(changeSet.changedFields()));
        application.setUnchangedFields(jsonValue(changeSet.unchangedFields()));
        application.setAppliedBy(appliedBy);
        application.setAppliedAt(now);
        application.setCreateTime(now);
        application.setUpdateTime(now);
        application.setDeleteFlag(0);
        applicationMapper.insert(application);
    }

    private void markApplied(ActivityPollEntity poll) {
        poll.setResultApplyStatus("APPLIED");
        poll.setAppliedAt(LocalDateTime.now());
        poll.setUpdateTime(LocalDateTime.now());
        poll.setVersion(poll.getVersion() + 1);
        pollMapper.updateById(poll);
        todoLifecycleService.onResultApplied(poll);
    }

    private void review(ActivityPollEntity poll, ActivityEntity activity) {
        poll.setResultApplyStatus("REVIEW_REQUIRED");
        poll.setUpdateTime(LocalDateTime.now());
        poll.setVersion(poll.getVersion() + 1);
        pollMapper.updateById(poll);
        todoLifecycleService.onReviewRequired(poll);
    }

    private PollDetailResponse detailInternal(Long activityId, Long pollId, Long userId) {
        ActivityPollEntity poll = requirePoll(activityId, pollId);
        List<ActivityPollOptionEntity> options = optionMapper.selectList(
                new LambdaQueryWrapper<ActivityPollOptionEntity>()
                        .eq(ActivityPollOptionEntity::getPollId, pollId)
                        .orderByAsc(ActivityPollOptionEntity::getSortNo));
        List<ActivityPollVoteEntity> votes = voteMapper.selectList(
                new LambdaQueryWrapper<ActivityPollVoteEntity>()
                        .eq(ActivityPollVoteEntity::getPollId, pollId));
        Map<Long, Long> counts = votes.stream().collect(
                Collectors.groupingBy(ActivityPollVoteEntity::getOptionId, Collectors.counting()));
        List<ActivityPollVoteEntity> currentVotes = votes.stream()
                .filter(vote -> userId.equals(vote.getUserId()))
                .sorted(Comparator.comparing(
                        ActivityPollVoteEntity::getUpdateTime,
                        Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .toList();
        if ("SINGLE".equals(poll.getVoteType()) && currentVotes.size() > 1) {
            log.warn("single vote data inconsistent: pollId={}, userId={}, activeOptionIds={}",
                    pollId, userId, currentVotes.stream().map(ActivityPollVoteEntity::getOptionId).toList());
            currentVotes = currentVotes.subList(0, 1);
        }
        Set<Long> selected = currentVotes.stream().map(ActivityPollVoteEntity::getOptionId)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        ActivityItineraryEntity target = poll.getTargetItineraryId() == null
                ? null : itineraryMapper.selectById(poll.getTargetItineraryId());
        List<String> scope = decisionScope(poll);
        PollResultPreviewResponse preview = null;
        if (!"APPLIED".equals(poll.getResultApplyStatus()) && poll.getWinnerOptionId() != null
                && !"GENERAL".equals(poll.getPurpose())) {
            preview = buildPreview(poll, requireOption(pollId, poll.getWinnerOptionId()));
        }

        return new PollDetailResponse(
                poll.getId(), poll.getActivityId(), poll.getTitle(), poll.getDescription(),
                poll.getPurpose(), poll.getDecisionType(), poll.getVoteType(),
                Integer.valueOf(1).equals(poll.getAllowModify()), poll.getDeadline(), poll.getStatus(),
                poll.getResultApplyMode(), poll.getResultApplyStatus(), poll.getTargetItineraryId(),
                target == null ? null : target.getTitle(), poll.getGeneratedItineraryId(),
                poll.getWinnerOptionId(), targetSummary(target), jsonMap(poll.getItineraryTemplate()),
                scope, fieldPolicy.labels(scope), fieldPolicy.unchangedLabels(scope), poll.getCreatedBy(),
                poll.getClosedAt(), poll.getAppliedAt(),
                votes.stream().map(ActivityPollVoteEntity::getUserId).distinct().count(),
                selected.stream().toList(),
                options.stream().map(option -> new PollOptionResponse(
                        option.getId(), option.getOptionText(), option.getOptionDescription(),
                        jsonMap(option.getResultPayload()), option.getSortNo(),
                        counts.getOrDefault(option.getId(), 0L), selected.contains(option.getId())))
                        .toList(),
                preview, applicationHistory(pollId));
    }

    private List<PollListItemResponse> listInternal(Long activityId, Long userId) {
        List<ActivityPollEntity> polls = pollMapper.selectList(
                new LambdaQueryWrapper<ActivityPollEntity>()
                        .eq(ActivityPollEntity::getActivityId, activityId)
                        .orderByDesc(ActivityPollEntity::getCreateTime));
        Map<Long, String> applicationSummaries = applicationMapper.selectList(
                        new LambdaQueryWrapper<ActivityPollApplicationEntity>()
                                .eq(ActivityPollApplicationEntity::getActivityId, activityId))
                .stream().collect(Collectors.toMap(
                        ActivityPollApplicationEntity::getPollId,
                        this::applicationSummary,
                        (first, ignored) -> first));
        return polls.stream().map(poll -> {
            List<ActivityPollVoteEntity> votes = voteMapper.selectList(
                    new LambdaQueryWrapper<ActivityPollVoteEntity>()
                            .eq(ActivityPollVoteEntity::getPollId, poll.getId()));
            ActivityItineraryEntity target = poll.getTargetItineraryId() == null
                    ? null : itineraryMapper.selectById(poll.getTargetItineraryId());
            return new PollListItemResponse(
                    poll.getId(), poll.getTitle(), poll.getPurpose(), poll.getDecisionType(),
                    poll.getVoteType(), poll.getStatus(), poll.getResultApplyStatus(),
                    poll.getTargetItineraryId(), poll.getGeneratedItineraryId(),
                    target == null ? null : target.getTitle(), poll.getDeadline(),
                    votes.stream().map(ActivityPollVoteEntity::getUserId).distinct().count(),
                    votes.stream().anyMatch(vote -> userId.equals(vote.getUserId())),
                    poll.getWinnerOptionId(), applicationSummaries.get(poll.getId()));
        }).toList();
    }

    private List<PollApplicationHistoryResponse> applicationHistory(Long pollId) {
        return applicationMapper.selectList(
                        new LambdaQueryWrapper<ActivityPollApplicationEntity>()
                                .eq(ActivityPollApplicationEntity::getPollId, pollId)
                                .orderByDesc(ActivityPollApplicationEntity::getAppliedAt))
                .stream().map(application -> {
                    UserEntity user = userMapper.selectById(application.getAppliedBy());
                    return new PollApplicationHistoryResponse(
                            application.getId(), application.getPollId(),
                            application.getTargetItineraryId(), application.getWinnerOptionId(),
                            application.getAppliedBy(), user == null ? null : user.getNickname(),
                            application.getAppliedAt(), jsonMap(application.getBeforeSnapshot()),
                            jsonMap(application.getAfterSnapshot()),
                            jsonList(application.getChangedFields(), new TypeReference<>() {}),
                            jsonList(application.getUnchangedFields(), new TypeReference<>() {}));
                }).toList();
    }

    private String applicationSummary(ActivityPollApplicationEntity application) {
        List<PollFieldChangeResponse> changes = jsonList(
                application.getChangedFields(), new TypeReference<>() {});
        return changes.stream().map(change -> change.label() + "："
                        + displayValue(change.beforeValue()) + " → " + displayValue(change.afterValue()))
                .collect(Collectors.joining("；"));
    }

    private Map<String, Object> targetSummary(ActivityItineraryEntity target) {
        if (target == null) return Map.of();
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("itineraryId", target.getId());
        result.put("createdBy", target.getCreatedBy());
        result.put("title", target.getTitle());
        result.put("itineraryType", target.getItineraryType());
        result.put("itineraryDate", target.getItineraryDate());
        result.put("startTime", target.getStartTime());
        result.put("endTime", target.getEndTime());
        result.putAll(fieldPolicy.snapshot(target));
        return result;
    }

    private List<String> decisionScope(ActivityPollEntity poll) {
        List<String> stored = jsonStringList(poll.getDecisionScope());
        return fieldPolicy.normalizeStoredScope(poll.getPurpose(), poll.getDecisionType(), stored);
    }

    private Map<String, Object> normalizeItineraryTemplate(Map<String, Object> source) {
        if (source == null || source.isEmpty()) return Map.of();
        LinkedHashMap<String, Object> template = new LinkedHashMap<>(source);
        String routeDetail = string(template, "routeDetail");
        String description = typePolicy.mergeLegacyRouteDetail(
                string(template, "description"), routeDetail);
        template.remove("routeDetail");
        if (StringUtils.hasText(description)) {
            template.put("description", description);
        } else {
            template.remove("description");
        }
        if (template.containsKey("itineraryType")) {
            template.put("itineraryType", typePolicy.normalizeType(
                    string(template, "itineraryType")));
        }
        return template;
    }

    private void validateItineraryTemplate(
            Map<String, Object> template,
            String itineraryType
    ) {
        if (!StringUtils.hasText(string(template, "title"))
                || date(template, "itineraryDate") == null) {
            throw param("生成行程需要固定的行程名称和日期");
        }
        LinkedHashMap<String, String> typedFields = new LinkedHashMap<>();
        typedFields.put("transportMode", string(template, "transportMode"));
        typedFields.put("departureName", string(template, "departureName"));
        typedFields.put("destinationName", string(template, "destinationName"));
        typedFields.put("mealType", string(template, "mealType"));
        typedFields.put("restaurantName", string(template, "restaurantName"));
        typedFields.put("activityContent", string(template, "activityContent"));
        typedFields.put("locationName", string(template, "locationName"));
        typedFields.put("address", string(template, "address"));
        typePolicy.validateRequestedFields(itineraryType, typedFields);
        typePolicy.validateTimes(
                itineraryType,
                time(template, "startTime"),
                time(template, "endTime"),
                booleanValue(template, "allDay") ? 1 : 0);
    }

    private ActivityPollEntity requirePoll(Long activityId, Long pollId) {
        ActivityPollEntity poll = pollMapper.selectById(pollId);
        if (poll == null || !activityId.equals(poll.getActivityId())) {
            throw new NotFoundException("投票不存在");
        }
        return poll;
    }

    private ActivityPollOptionEntity requireOption(Long pollId, Long optionId) {
        ActivityPollOptionEntity option = optionMapper.selectById(optionId);
        if (option == null || !pollId.equals(option.getPollId())) {
            throw new NotFoundException("投票选项不存在");
        }
        return option;
    }

    private ActivityItineraryEntity requireTarget(ActivityPollEntity poll) {
        ActivityItineraryEntity itinerary = itineraryMapper.selectById(poll.getTargetItineraryId());
        if (itinerary == null || !poll.getActivityId().equals(itinerary.getActivityId())) {
            throw param("关联行程不存在");
        }
        return itinerary;
    }

    private void requirePollManager(
            ActivityEntity activity,
            ActivityMemberEntity member,
            Long userId,
            ActivityPollEntity poll
    ) {
        if (!userId.equals(poll.getCreatedBy()) && !access.isActivityCreator(activity, member, userId)) {
            throw new ForbiddenException("只能管理自己创建的投票");
        }
    }

    private String jsonObject(Map<String, Object> value) {
        return value == null || value.isEmpty() ? null : jsonValue(value);
    }

    private String jsonValue(Object value) {
        if (value == null) return null;
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw param("投票结果内容格式错误");
        }
    }

    private Map<String, Object> jsonMap(String json) {
        if (!StringUtils.hasText(json)) return new LinkedHashMap<>();
        try {
            return objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {});
        } catch (JsonProcessingException ex) {
            throw new BusinessException("投票数据格式异常");
        }
    }

    private List<String> jsonStringList(String json) {
        if (!StringUtils.hasText(json)) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException ex) {
            throw new BusinessException("投票字段范围格式异常");
        }
    }

    private <T> List<T> jsonList(String json, TypeReference<List<T>> type) {
        if (!StringUtils.hasText(json)) return List.of();
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException ex) {
            throw new BusinessException("投票应用记录格式异常");
        }
    }

    private String string(Map<String, Object> values, String key) {
        Object value = values.get(key);
        return value == null ? null : String.valueOf(value).trim();
    }

    private boolean booleanValue(Map<String, Object> values, String key) {
        Object value = values.get(key);
        return value instanceof Boolean bool ? bool : Boolean.parseBoolean(String.valueOf(value));
    }

    private LocalDate date(Map<String, Object> values, String key) {
        String value = string(values, key);
        return StringUtils.hasText(value) ? LocalDate.parse(value) : null;
    }

    private LocalTime time(Map<String, Object> values, String key) {
        String value = string(values, key);
        return StringUtils.hasText(value) ? LocalTime.parse(value) : null;
    }

    private String upper(String value) {
        return value == null ? null : value.trim().toUpperCase();
    }

    private String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String displayValue(Object value) {
        return value == null || !StringUtils.hasText(String.valueOf(value)) ? "未设置" : String.valueOf(value);
    }

    private BusinessException param(String message) {
        return new BusinessException(ErrorCode.PARAM_ERROR.code(), message);
    }
}
