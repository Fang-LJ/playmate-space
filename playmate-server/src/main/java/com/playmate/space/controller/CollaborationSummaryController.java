package com.playmate.space.controller;
import com.playmate.space.common.ApiResponse;
import com.playmate.space.dto.collaboration.CollaborationSummaryResponse;
import com.playmate.space.service.CollaborationSummaryService;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/activities/{activityId}/collaboration-summary")
public class CollaborationSummaryController {
 private final CollaborationSummaryService service; public CollaborationSummaryController(CollaborationSummaryService service){this.service=service;}
 @GetMapping public ApiResponse<CollaborationSummaryResponse> get(@PathVariable Long activityId){return ApiResponse.success(service.get(activityId));}
}
