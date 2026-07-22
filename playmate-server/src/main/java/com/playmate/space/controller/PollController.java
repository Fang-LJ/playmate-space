package com.playmate.space.controller;

import com.playmate.space.common.ApiResponse;
import com.playmate.space.dto.poll.*;
import com.playmate.space.service.PollService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/activities/{activityId}/polls")
public class PollController {
 private final PollService pollService; public PollController(PollService pollService){this.pollService=pollService;}
 @GetMapping public ApiResponse<List<PollListItemResponse>> list(@PathVariable Long activityId){return ApiResponse.success(pollService.list(activityId));}
 @PostMapping public ApiResponse<PollDetailResponse> create(@PathVariable Long activityId,@Valid @RequestBody CreatePollRequest request){return ApiResponse.success(pollService.create(activityId,request));}
 @GetMapping("/{pollId}") public ApiResponse<PollDetailResponse> detail(@PathVariable Long activityId,@PathVariable Long pollId){return ApiResponse.success(pollService.detail(activityId,pollId));}
 @PutMapping("/{pollId}") public ApiResponse<PollDetailResponse> update(@PathVariable Long activityId,@PathVariable Long pollId,@Valid @RequestBody UpdatePollRequest request){return ApiResponse.success(pollService.update(activityId,pollId,request));}
 @PostMapping("/{pollId}/votes") public ApiResponse<PollDetailResponse> vote(@PathVariable Long activityId,@PathVariable Long pollId,@Valid @RequestBody VoteRequest request){return ApiResponse.success(pollService.submitVote(activityId,pollId,request));}
 @PostMapping("/{pollId}/close") public ApiResponse<PollDetailResponse> close(@PathVariable Long activityId,@PathVariable Long pollId){return ApiResponse.success(pollService.close(activityId,pollId));}
 @PostMapping("/{pollId}/cancel") public ApiResponse<PollDetailResponse> cancel(@PathVariable Long activityId,@PathVariable Long pollId){return ApiResponse.success(pollService.cancel(activityId,pollId));}
 @GetMapping("/{pollId}/result-preview") public ApiResponse<PollResultPreviewResponse> preview(@PathVariable Long activityId,@PathVariable Long pollId,@RequestParam Long optionId){return ApiResponse.success(pollService.previewResult(activityId,pollId,optionId));}
 @PostMapping("/{pollId}/apply-result") public ApiResponse<PollDetailResponse> apply(@PathVariable Long activityId,@PathVariable Long pollId,@Valid @RequestBody ApplyPollResultRequest request){return ApiResponse.success(pollService.applyResult(activityId,pollId,request));}
}
