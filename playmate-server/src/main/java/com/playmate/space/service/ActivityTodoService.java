package com.playmate.space.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.playmate.space.dto.activity.ActivityListItemResponse;
import com.playmate.space.dto.collaboration.ActivityTodoItemResponse;
import com.playmate.space.dto.collaboration.UserActivityTodosResponse;
import com.playmate.space.entity.ActivityItineraryEntity;
import com.playmate.space.entity.ActivityMemberEntity;
import com.playmate.space.entity.ActivityPollEntity;
import com.playmate.space.entity.ActivityPollVoteEntity;
import com.playmate.space.mapper.ActivityItineraryMapper;
import com.playmate.space.mapper.ActivityMapper;
import com.playmate.space.mapper.ActivityMemberMapper;
import com.playmate.space.mapper.ActivityPollMapper;
import com.playmate.space.mapper.ActivityPollVoteMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ActivityTodoService {
    private final ActivityCollaborationAccess access;
    private final ActivityMapper activityMapper;
    private final ActivityMemberMapper memberMapper;
    private final ActivityPollMapper pollMapper;
    private final ActivityPollVoteMapper voteMapper;
    private final ActivityItineraryMapper itineraryMapper;
    private final PollService pollService;

    public ActivityTodoService(ActivityCollaborationAccess access, ActivityMapper activityMapper,
                               ActivityMemberMapper memberMapper, ActivityPollMapper pollMapper,
                               ActivityPollVoteMapper voteMapper, ActivityItineraryMapper itineraryMapper,
                               PollService pollService) {
        this.access = access;
        this.activityMapper = activityMapper;
        this.memberMapper = memberMapper;
        this.pollMapper = pollMapper;
        this.voteMapper = voteMapper;
        this.itineraryMapper = itineraryMapper;
        this.pollService = pollService;
    }

    public UserActivityTodosResponse getCurrentUserTodos() {
        Long userId = access.requireUserId();
        List<ActivityTodoItemResponse> todos = new ArrayList<>();
        for (ActivityListItemResponse activity : activityMapper.selectMyActivities(userId)) {
            if ("ENDED".equals(activity.getStatus()) || "CANCELED".equals(activity.getStatus())) continue;
            todos.addAll(getForActivity(activity.getActivityId(), activity.getName(), userId));
        }
        todos.sort(Comparator.comparing(ActivityTodoItemResponse::dueAt,
                Comparator.nullsLast(Comparator.naturalOrder())));
        return new UserActivityTodosResponse((long) todos.size(), todos);
    }

    public List<ActivityTodoItemResponse> getForActivity(Long activityId, String activityName, Long userId) {
        // Reuse the existing idempotent expiry finalizer before aggregating active poll todos.
        pollService.list(activityId);
        LocalDateTime now = LocalDateTime.now();
        List<ActivityTodoItemResponse> todos = new ArrayList<>();
        List<ActivityPollEntity> polls = pollMapper.selectList(new LambdaQueryWrapper<ActivityPollEntity>()
                .eq(ActivityPollEntity::getActivityId, activityId)
                .eq(ActivityPollEntity::getStatus, "ACTIVE"));
        Set<Long> votedPollIds = voteMapper.selectList(new LambdaQueryWrapper<ActivityPollVoteEntity>()
                        .eq(ActivityPollVoteEntity::getUserId, userId))
                .stream().map(ActivityPollVoteEntity::getPollId).collect(Collectors.toSet());
        for (ActivityPollEntity poll : polls) {
            if (!votedPollIds.contains(poll.getId())) {
                todos.add(pollTodo(activityId, activityName, poll, "POLL_PENDING", "参与投票", "去投票"));
            }
            if (poll.getDeadline() != null && !poll.getDeadline().isBefore(now)
                    && !poll.getDeadline().isAfter(now.plusHours(24))) {
                todos.add(pollTodo(activityId, activityName, poll, "POLL_DUE_SOON", "投票即将截止", "查看投票"));
            }
        }
        for (ActivityPollEntity poll : pollMapper.selectList(new LambdaQueryWrapper<ActivityPollEntity>()
                .eq(ActivityPollEntity::getActivityId, activityId)
                .eq(ActivityPollEntity::getResultApplyStatus, "REVIEW_REQUIRED"))) {
            if (canConfirmResult(activityId, poll, userId)) {
                todos.add(new ActivityTodoItemResponse(activityId, activityName, "POLL", poll.getId(),
                        "POLL_REVIEW_REQUIRED", "确认投票结果：" + poll.getTitle(), "结果需要由有权限的成员确认应用",
                        poll.getClosedAt(), "确认结果"));
            }
        }
        for (ActivityItineraryEntity itinerary : itineraryMapper.selectList(new LambdaQueryWrapper<ActivityItineraryEntity>()
                .eq(ActivityItineraryEntity::getActivityId, activityId)
                .ne(ActivityItineraryEntity::getPlanningStatus, "CANCELED"))) {
            LocalDateTime start = itinerary.getStartTime() == null
                    ? itinerary.getItineraryDate().atStartOfDay()
                    : itinerary.getItineraryDate().atTime(itinerary.getStartTime());
            String timeStatus = ItineraryTimeStatusResolver.resolve(itinerary, now);
            if ("IN_PROGRESS".equals(timeStatus)) {
                todos.add(new ActivityTodoItemResponse(activityId, activityName, "ITINERARY", itinerary.getId(),
                        "ITINERARY_IN_PROGRESS", "正在进行：" + itinerary.getTitle(), itinerary.getLocationName(),
                        start, "查看行程"));
            } else if ("UPCOMING".equals(timeStatus) && !start.isAfter(now.plusHours(24))) {
                todos.add(new ActivityTodoItemResponse(activityId, activityName, "ITINERARY",
                        itinerary.getId(), "ITINERARY_STARTS_SOON", "即将开始：" + itinerary.getTitle(),
                        itinerary.getLocationName(), start, "查看行程"));
            }
        }
        return todos;
    }

    private ActivityTodoItemResponse pollTodo(Long activityId, String activityName, ActivityPollEntity poll,
                                              String todoType, String prefix, String actionText) {
        String description = poll.getDeadline() == null ? "暂无截止时间" : "截止：" + poll.getDeadline();
        return new ActivityTodoItemResponse(activityId, activityName, "POLL", poll.getId(), todoType,
                prefix + "：" + poll.getTitle(), description, poll.getDeadline(), actionText);
    }

    private boolean canConfirmResult(Long activityId, ActivityPollEntity poll, Long userId) {
        if (userId.equals(poll.getCreatedBy())) return true;
        ActivityMemberEntity member = memberMapper.selectOne(new LambdaQueryWrapper<ActivityMemberEntity>()
                .eq(ActivityMemberEntity::getActivityId, activityId)
                .eq(ActivityMemberEntity::getUserId, userId)
                .eq(ActivityMemberEntity::getMemberStatus, "ACTIVE")
                .last("LIMIT 1"));
        if (member != null && ActivityCollaborationAccess.ROLE_CREATOR.equals(member.getRole())) return true;
        if (poll.getTargetItineraryId() == null) return false;
        ActivityItineraryEntity itinerary = itineraryMapper.selectById(poll.getTargetItineraryId());
        return itinerary != null && userId.equals(itinerary.getCreatedBy());
    }
}
