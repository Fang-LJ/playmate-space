package com.playmate.space.service;

import com.playmate.space.dto.collaboration.ActivityTodoItemResponse;
import com.playmate.space.dto.collaboration.CollaborationSummaryResponse;
import com.playmate.space.dto.collaboration.CollaborationTodoResponse;
import com.playmate.space.dto.itinerary.ItineraryResponse;
import com.playmate.space.dto.poll.PollListItemResponse;
import com.playmate.space.entity.ActivityEntity;
import com.playmate.space.entity.ActivityItineraryEntity;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class CollaborationSummaryService {
    private final ActivityCollaborationAccess access; private final ItineraryService itineraryService;
    private final PollService pollService; private final ActivityTodoService activityTodoService;
    public CollaborationSummaryService(ActivityCollaborationAccess access, ItineraryService itineraryService, PollService pollService, ActivityTodoService activityTodoService) {
        this.access=access; this.itineraryService=itineraryService; this.pollService=pollService; this.activityTodoService=activityTodoService;
    }
    public CollaborationSummaryResponse get(Long activityId) {
        Long userId=access.requireUserId(); ActivityEntity activity=access.requireActivity(activityId); access.requireActiveMember(activityId,userId);
        List<ActivityItineraryEntity> itineraries=itineraryService.sorted(activityId);
        List<PollListItemResponse> polls=pollService.list(activityId);
        List<ActivityTodoItemResponse> todos=activityTodoService.getForActivity(activityId,userId);
        ItineraryResponse next=itineraries.stream().filter(i->!"CANCELED".equals(i.getPlanningStatus()))
                .filter(i->!"FINISHED".equals(ItineraryTimeStatusResolver.resolve(i,LocalDateTime.now())))
                .findFirst().map(itineraryService::toResponse).orElse(null);
        long pending=polls.stream().filter(p->"ACTIVE".equals(p.status())&&!Boolean.TRUE.equals(p.currentUserVoted())).count();
        long review=polls.stream().filter(p->"REVIEW_REQUIRED".equals(p.resultApplyStatus())).count();
        long active=polls.stream().filter(p->"ACTIVE".equals(p.status())).count();
        long today=itineraries.stream().filter(i->LocalDate.now().equals(i.getItineraryDate())).count();
        String defaultTab="ITINERARIES"; if("PLANNING".equals(activity.getStatus())&&itineraries.isEmpty()&&pending>0) defaultTab="POLLS";
        return new CollaborationSummaryResponse(defaultTab,(long)todos.size(),todos.stream().limit(3)
                .map(todo->new CollaborationTodoResponse(todo.getTodoType(),todo.getTitle(),todo.getSourceType(),todo.getSourceId())).toList(),
                (long)itineraries.size(),today,next,active,pending,review);
    }
}
