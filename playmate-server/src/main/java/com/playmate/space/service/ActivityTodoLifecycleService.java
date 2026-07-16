package com.playmate.space.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.playmate.space.common.ErrorCode;
import com.playmate.space.common.exception.BusinessException;
import com.playmate.space.common.exception.ForbiddenException;
import com.playmate.space.common.exception.NotFoundException;
import com.playmate.space.dto.todo.CreateActivityReminderRequest;
import com.playmate.space.dto.todo.ReminderAckMemberResponse;
import com.playmate.space.dto.todo.ReminderAckStatusResponse;
import com.playmate.space.entity.*;
import com.playmate.space.mapper.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/** Persists business-triggered activity tasks. It never derives tasks in read requests. */
@Service
public class ActivityTodoLifecycleService {
    public static final String TODO_POLL_VOTE = "POLL_VOTE";
    public static final String TODO_POLL_RESULT_CONFIRM = "POLL_RESULT_CONFIRM";
    public static final String TODO_MANUAL_REMINDER = "MANUAL_REMINDER";
    private static final String ACTIVE = "ACTIVE";
    private static final String PENDING = "PENDING";
    private static final String COMPLETED = "COMPLETED";
    private static final String CANCELED = "CANCELED";

    private final ActivityCollaborationAccess access;
    private final ActivityTodoMapper todoMapper;
    private final ActivityTodoUserMapper todoUserMapper;
    private final ActivityMemberMapper memberMapper;
    private final ActivityPollVoteMapper voteMapper;
    private final ActivityItineraryMapper itineraryMapper;
    private final UserMapper userMapper;

    public ActivityTodoLifecycleService(ActivityCollaborationAccess access, ActivityTodoMapper todoMapper,
                                        ActivityTodoUserMapper todoUserMapper, ActivityMemberMapper memberMapper,
                                        ActivityPollVoteMapper voteMapper, ActivityItineraryMapper itineraryMapper,
                                        UserMapper userMapper) {
        this.access = access; this.todoMapper = todoMapper; this.todoUserMapper = todoUserMapper;
        this.memberMapper = memberMapper; this.voteMapper = voteMapper; this.itineraryMapper = itineraryMapper;
        this.userMapper = userMapper;
    }

    /** Creates the poll task and assignments to all currently active members. */
    public void onPollCreated(ActivityPollEntity poll) {
        ActivityTodoEntity todo = ensureTodo(poll.getActivityId(), TODO_POLL_VOTE, "POLL", poll.getId(),
                "POLL_VOTE:" + poll.getId(), "参与投票：" + poll.getTitle(), pollContent(poll),
                "VIEW_POLL", poll.getDeadline(), true, poll.getCreatedBy());
        assignPollTodoToActiveMembers(todo, poll);
    }

    /** A later member should receive still-active poll tasks, without touching historical/closed polls. */
    public void onMemberJoined(Long activityId, Long userId, List<ActivityPollEntity> activePolls) {
        for (ActivityPollEntity poll : activePolls) {
            if (!ACTIVE.equals(poll.getStatus())) continue;
            ActivityTodoEntity todo = ensureTodo(activityId, TODO_POLL_VOTE, "POLL", poll.getId(),
                    "POLL_VOTE:" + poll.getId(), "参与投票：" + poll.getTitle(), pollContent(poll),
                    "VIEW_POLL", poll.getDeadline(), true, poll.getCreatedBy());
            assignPollTodo(todo, poll, userId);
        }
    }

    public void onUserVoted(Long pollId, Long userId) {
        findTodoBySourceKey("POLL_VOTE:" + pollId).ifPresent(todo -> completeUser(todo.getId(), userId, "VOTED"));
    }

    public void closePollVoteTodos(ActivityPollEntity poll, boolean expired) {
        findTodo(poll.getActivityId(), "POLL_VOTE:" + poll.getId()).ifPresent(todo ->
                closeTodo(todo, expired ? "EXPIRED" : COMPLETED, COMPLETED, "AUTO_CLOSED"));
    }

    public void cancelPollTodos(ActivityPollEntity poll) {
        findTodo(poll.getActivityId(), "POLL_VOTE:" + poll.getId()).ifPresent(todo -> closeTodo(todo, CANCELED, CANCELED, "AUTO_CLOSED"));
        findTodo(poll.getActivityId(), "POLL_RESULT_CONFIRM:" + poll.getId()).ifPresent(todo -> closeTodo(todo, CANCELED, CANCELED, "AUTO_CLOSED"));
    }

    public void onReviewRequired(ActivityPollEntity poll) {
        ActivityTodoEntity todo = ensureTodo(poll.getActivityId(), TODO_POLL_RESULT_CONFIRM, "POLL", poll.getId(),
                "POLL_RESULT_CONFIRM:" + poll.getId(), "确认投票结果：" + poll.getTitle(),
                "投票结果需要由有权限的成员确认应用", "CONFIRM_POLL_RESULT", poll.getClosedAt(), true, poll.getCreatedBy());
        for (Long userId : resultConfirmers(poll)) ensureAssignment(todo.getId(), userId, PENDING, null);
    }

    public void onResultApplied(ActivityPollEntity poll) {
        findTodo(poll.getActivityId(), "POLL_RESULT_CONFIRM:" + poll.getId()).ifPresent(todo ->
                closeTodo(todo, COMPLETED, COMPLETED, "RESULT_CONFIRMED"));
    }

    @Transactional
    public Long publishManualReminder(Long activityId, CreateActivityReminderRequest request) {
        Long userId = access.requireUserId(); ActivityEntity activity = access.requireActivity(activityId);
        ActivityMemberEntity member = access.requireActiveMember(activityId, userId);
        access.requireWritableActivity(activity);
        if (!access.isActivityCreator(activity, member, userId)) throw new ForbiddenException("只有活动创建者可以发布提醒");
        LocalDateTime now = LocalDateTime.now();
        ActivityTodoEntity todo = new ActivityTodoEntity();
        todo.setActivityId(activityId); todo.setTodoType(TODO_MANUAL_REMINDER); todo.setSourceType("MANUAL");
        todo.setSourceKey("MANUAL:" + UUID.randomUUID()); todo.setTitle(request.title().trim());
        todo.setContent(trim(request.content())); todo.setActionType("ACK_REMINDER"); todo.setDueTime(request.dueTime());
        todo.setStatus(ACTIVE); todo.setAutoGenerated(0); todo.setCreatedBy(userId); todo.setVersion(0);
        todo.setCreateTime(now); todo.setUpdateTime(now); todo.setDeleteFlag(0); todoMapper.insert(todo);
        for (ActivityMemberEntity activeMember : activeMembers(activityId)) ensureAssignment(todo.getId(), activeMember.getUserId(), PENDING, null);
        return todo.getId();
    }

    @Transactional
    public void acknowledgeReminder(Long todoId) {
        Long userId = access.requireUserId(); ActivityTodoEntity todo = requireTodo(todoId);
        if (!TODO_MANUAL_REMINDER.equals(todo.getTodoType())) throw new BusinessException(ErrorCode.PARAM_ERROR.code(), "当前待办不支持确认");
        access.requireActiveMember(todo.getActivityId(), userId);
        ActivityTodoUserEntity assignment = todoUserMapper.selectOne(new LambdaQueryWrapper<ActivityTodoUserEntity>()
                .eq(ActivityTodoUserEntity::getTodoId, todoId).eq(ActivityTodoUserEntity::getUserId, userId).last("LIMIT 1"));
        if (assignment == null) throw new ForbiddenException("该提醒未分配给当前用户");
        if (COMPLETED.equals(assignment.getStatus())) return;
        if (!PENDING.equals(assignment.getStatus())) throw new BusinessException(ErrorCode.PARAM_ERROR.code(), "当前提醒已关闭");
        LocalDateTime now = LocalDateTime.now(); assignment.setStatus(COMPLETED); assignment.setCompletedTime(now);
        assignment.setCompletionReason("ACKNOWLEDGED"); assignment.setUpdateTime(now); todoUserMapper.updateById(assignment);
    }

    public ReminderAckStatusResponse getReminderAckStatus(Long activityId, Long todoId) {
        Long userId = access.requireUserId(); ActivityEntity activity = access.requireActivity(activityId);
        ActivityMemberEntity member = access.requireActiveMember(activityId, userId);
        if (!access.isActivityCreator(activity, member, userId)) throw new ForbiddenException("只有活动创建者可以查看确认情况");
        ActivityTodoEntity todo = requireTodo(todoId);
        if (!activityId.equals(todo.getActivityId()) || !TODO_MANUAL_REMINDER.equals(todo.getTodoType())) throw new NotFoundException("提醒不存在");
        List<ActivityTodoUserEntity> assignments = todoUserMapper.selectList(new LambdaQueryWrapper<ActivityTodoUserEntity>()
                .eq(ActivityTodoUserEntity::getTodoId, todoId));
        Map<Long, String> names = userMapper.selectBatchIds(assignments.stream().map(ActivityTodoUserEntity::getUserId).toList())
                .stream().collect(Collectors.toMap(UserEntity::getId, user -> StringUtils.hasText(user.getNickname()) ? user.getNickname() : "玩伴用户"));
        List<ReminderAckMemberResponse> members = assignments.stream().map(item -> new ReminderAckMemberResponse(
                item.getUserId(), names.getOrDefault(item.getUserId(), "玩伴用户"), item.getStatus(), item.getCompletedTime())).toList();
        long acknowledged = assignments.stream().filter(item -> COMPLETED.equals(item.getStatus())).count();
        return new ReminderAckStatusResponse(todoId, acknowledged, (long) assignments.size() - acknowledged, members);
    }

    public void cancelActivityPendingTodos(Long activityId) {
        List<ActivityTodoEntity> todos = todoMapper.selectList(new LambdaQueryWrapper<ActivityTodoEntity>()
                .eq(ActivityTodoEntity::getActivityId, activityId).eq(ActivityTodoEntity::getStatus, ACTIVE));
        for (ActivityTodoEntity todo : todos) closeTodo(todo, CANCELED, CANCELED, "ACTIVITY_CANCELED");
    }

    public void cancelUserPendingTodos(Long activityId, Long userId) {
        for (ActivityTodoEntity todo : todoMapper.selectList(new LambdaQueryWrapper<ActivityTodoEntity>()
                .eq(ActivityTodoEntity::getActivityId, activityId).eq(ActivityTodoEntity::getStatus, ACTIVE))) {
            closeOneUser(todo.getId(), userId, CANCELED, "AUTO_CLOSED");
        }
    }

    /** Explicit, idempotent migration entrypoint. It is invoked only by the opt-in startup runner. */
    @Transactional
    public int backfillActivePollTodos(List<ActivityPollEntity> polls) {
        int count = 0;
        for (ActivityPollEntity poll : polls) {
            if (ACTIVE.equals(poll.getStatus())) { onPollCreated(poll); count++; }
            if ("REVIEW_REQUIRED".equals(poll.getResultApplyStatus())) { onReviewRequired(poll); count++; }
        }
        return count;
    }

    private void assignPollTodoToActiveMembers(ActivityTodoEntity todo, ActivityPollEntity poll) {
        for (ActivityMemberEntity member : activeMembers(poll.getActivityId())) assignPollTodo(todo, poll, member.getUserId());
    }
    private void assignPollTodo(ActivityTodoEntity todo, ActivityPollEntity poll, Long userId) {
        boolean voted = voteMapper.selectCount(new LambdaQueryWrapper<ActivityPollVoteEntity>()
                .eq(ActivityPollVoteEntity::getPollId, poll.getId()).eq(ActivityPollVoteEntity::getUserId, userId)) > 0;
        ensureAssignment(todo.getId(), userId, voted ? COMPLETED : PENDING, voted ? "VOTED" : null);
    }
    private List<ActivityMemberEntity> activeMembers(Long activityId) {
        return memberMapper.selectList(new LambdaQueryWrapper<ActivityMemberEntity>()
                .eq(ActivityMemberEntity::getActivityId, activityId).eq(ActivityMemberEntity::getMemberStatus, "ACTIVE"));
    }
    private Set<Long> resultConfirmers(ActivityPollEntity poll) {
        List<ActivityMemberEntity> members = activeMembers(poll.getActivityId());
        Set<Long> activeUserIds = members.stream().map(ActivityMemberEntity::getUserId).collect(Collectors.toSet());
        Set<Long> users = new LinkedHashSet<>(); users.add(poll.getCreatedBy());
        for (ActivityMemberEntity member : members) if (ActivityCollaborationAccess.ROLE_CREATOR.equals(member.getRole())) users.add(member.getUserId());
        if (poll.getTargetItineraryId() != null) { ActivityItineraryEntity itinerary = itineraryMapper.selectById(poll.getTargetItineraryId()); if (itinerary != null) users.add(itinerary.getCreatedBy()); }
        users.retainAll(activeUserIds);
        return users;
    }
    private ActivityTodoEntity ensureTodo(Long activityId, String todoType, String sourceType, Long sourceId, String sourceKey,
                                          String title, String content, String actionType, LocalDateTime dueTime, boolean auto, Long createdBy) {
        Optional<ActivityTodoEntity> existing = findTodo(activityId, sourceKey); if (existing.isPresent()) return existing.get();
        LocalDateTime now = LocalDateTime.now(); ActivityTodoEntity todo = new ActivityTodoEntity();
        todo.setActivityId(activityId); todo.setTodoType(todoType); todo.setSourceType(sourceType); todo.setSourceId(sourceId); todo.setSourceKey(sourceKey);
        todo.setTitle(title); todo.setContent(content); todo.setActionType(actionType); todo.setDueTime(dueTime); todo.setStatus(ACTIVE);
        todo.setAutoGenerated(auto ? 1 : 0); todo.setCreatedBy(createdBy); todo.setVersion(0); todo.setCreateTime(now); todo.setUpdateTime(now); todo.setDeleteFlag(0);
        try { todoMapper.insert(todo); return todo; } catch (DuplicateKeyException ignored) { return findTodo(activityId, sourceKey).orElseThrow(() -> ignored); }
    }
    private Optional<ActivityTodoEntity> findTodo(Long activityId, String sourceKey) {
        return Optional.ofNullable(todoMapper.selectOne(new LambdaQueryWrapper<ActivityTodoEntity>()
                .eq(ActivityTodoEntity::getActivityId, activityId).eq(ActivityTodoEntity::getSourceKey, sourceKey).last("LIMIT 1")));
    }
    private Optional<ActivityTodoEntity> findTodoBySourceKey(String sourceKey) {
        return Optional.ofNullable(todoMapper.selectOne(new LambdaQueryWrapper<ActivityTodoEntity>()
                .eq(ActivityTodoEntity::getSourceKey, sourceKey).last("LIMIT 1")));
    }
    private ActivityTodoEntity requireTodo(Long todoId) { ActivityTodoEntity todo = todoMapper.selectById(todoId); if (todo == null) throw new NotFoundException("待办不存在"); return todo; }
    private void ensureAssignment(Long todoId, Long userId, String status, String reason) {
        ActivityTodoUserEntity current = todoUserMapper.selectOne(new LambdaQueryWrapper<ActivityTodoUserEntity>()
                .eq(ActivityTodoUserEntity::getTodoId, todoId).eq(ActivityTodoUserEntity::getUserId, userId).last("LIMIT 1"));
        if (current != null) return;
        LocalDateTime now = LocalDateTime.now(); ActivityTodoUserEntity item = new ActivityTodoUserEntity(); item.setTodoId(todoId); item.setUserId(userId);
        item.setStatus(status); item.setCompletedTime(PENDING.equals(status) ? null : now); item.setCompletionReason(reason); item.setCreateTime(now); item.setUpdateTime(now); item.setDeleteFlag(0);
        try { todoUserMapper.insert(item); } catch (DuplicateKeyException ignored) { /* idempotent concurrent delivery */ }
    }
    private void completeUser(Long todoId, Long userId, String reason) { closeOneUser(todoId, userId, COMPLETED, reason); }
    private void closeOneUser(Long todoId, Long userId, String status, String reason) {
        ActivityTodoUserEntity item = todoUserMapper.selectOne(new LambdaQueryWrapper<ActivityTodoUserEntity>()
                .eq(ActivityTodoUserEntity::getTodoId, todoId).eq(ActivityTodoUserEntity::getUserId, userId).last("LIMIT 1"));
        if (item == null || !PENDING.equals(item.getStatus())) return;
        LocalDateTime now = LocalDateTime.now(); item.setStatus(status); item.setCompletedTime(now); item.setCompletionReason(reason); item.setUpdateTime(now); todoUserMapper.updateById(item);
    }
    private void closeTodo(ActivityTodoEntity todo, String todoStatus, String userStatus, String reason) {
        if (!ACTIVE.equals(todo.getStatus())) return;
        LocalDateTime now = LocalDateTime.now();
        for (ActivityTodoUserEntity userTodo : todoUserMapper.selectList(new LambdaQueryWrapper<ActivityTodoUserEntity>()
                .eq(ActivityTodoUserEntity::getTodoId, todo.getId()).eq(ActivityTodoUserEntity::getStatus, PENDING))) {
            userTodo.setStatus(userStatus); userTodo.setCompletedTime(now); userTodo.setCompletionReason(reason); userTodo.setUpdateTime(now); todoUserMapper.updateById(userTodo);
        }
        todo.setStatus(todoStatus); todo.setVersion(todo.getVersion() + 1); todo.setUpdateTime(now); todoMapper.updateById(todo);
    }
    private String pollContent(ActivityPollEntity poll) { return poll.getDeadline() == null ? "暂无截止时间" : "截止：" + poll.getDeadline(); }
    private String trim(String value) { return StringUtils.hasText(value) ? value.trim() : null; }
}
