package com.playmate.space.controller;

import com.playmate.space.common.ApiResponse;
import com.playmate.space.dto.itinerary.*;
import com.playmate.space.dto.poll.PollListItemResponse;
import com.playmate.space.service.ItineraryService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/activities/{activityId}/itineraries")
public class ItineraryController {
    private final ItineraryService itineraryService;
    public ItineraryController(ItineraryService itineraryService){this.itineraryService=itineraryService;}
    @GetMapping public ApiResponse<List<ItineraryResponse>> list(@PathVariable Long activityId){return ApiResponse.success(itineraryService.list(activityId));}
    @GetMapping("/{itineraryId}") public ApiResponse<ItineraryDetailResponse> detail(@PathVariable Long activityId,@PathVariable Long itineraryId){return ApiResponse.success(itineraryService.detail(activityId,itineraryId));}
    @PostMapping public ApiResponse<ItineraryResponse> create(@PathVariable Long activityId,@Valid @RequestBody CreateItineraryRequest request){return ApiResponse.success(itineraryService.create(activityId,request));}
    @PutMapping("/{itineraryId}") public ApiResponse<ItineraryResponse> update(@PathVariable Long activityId,@PathVariable Long itineraryId,@Valid @RequestBody UpdateItineraryRequest request){return ApiResponse.success(itineraryService.update(activityId,itineraryId,request));}
    @PostMapping("/{itineraryId}/cancel") public ApiResponse<ItineraryResponse> cancel(@PathVariable Long activityId,@PathVariable Long itineraryId){return ApiResponse.success(itineraryService.cancel(activityId,itineraryId));}
    @GetMapping("/{itineraryId}/polls") public ApiResponse<List<PollListItemResponse>> polls(@PathVariable Long activityId,@PathVariable Long itineraryId){return ApiResponse.success(itineraryService.detail(activityId,itineraryId).relatedPolls());}
}
