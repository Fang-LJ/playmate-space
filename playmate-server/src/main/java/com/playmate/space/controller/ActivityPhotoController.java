package com.playmate.space.controller;

import com.playmate.space.common.ApiResponse;
import com.playmate.space.dto.photo.CreatePhotoResultResponse;
import com.playmate.space.dto.photo.CreatePhotosRequest;
import com.playmate.space.dto.photo.PhotoDetailResponse;
import com.playmate.space.dto.photo.PhotoOriginalUrlResponse;
import com.playmate.space.dto.photo.PhotoPageResponse;
import com.playmate.space.dto.photo.PhotoReportRequest;
import com.playmate.space.dto.photo.PhotoSummaryResponse;
import com.playmate.space.service.ActivityPhotoService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/activities/{activityId}/photos")
public class ActivityPhotoController {
    private final ActivityPhotoService photoService;

    public ActivityPhotoController(ActivityPhotoService photoService) {
        this.photoService = photoService;
    }

    @GetMapping("/summary")
    public ApiResponse<PhotoSummaryResponse> summary(@PathVariable Long activityId) {
        return ApiResponse.success(photoService.summary(activityId));
    }

    @GetMapping
    public ApiResponse<PhotoPageResponse> list(@PathVariable Long activityId,
                                                @RequestParam(required = false) String scope,
                                                @RequestParam(required = false) String sort,
                                                @RequestParam(required = false) Integer page,
                                                @RequestParam(required = false) Integer pageSize) {
        return ApiResponse.success(photoService.list(activityId, scope, sort, page, pageSize));
    }

    @PostMapping
    public ApiResponse<List<CreatePhotoResultResponse>> create(@PathVariable Long activityId,
                                                                 @Valid @RequestBody CreatePhotosRequest request) {
        return ApiResponse.success(photoService.create(activityId, request));
    }

    @GetMapping("/{photoId}")
    public ApiResponse<PhotoDetailResponse> detail(@PathVariable Long activityId, @PathVariable Long photoId) {
        return ApiResponse.success(photoService.detail(activityId, photoId));
    }

    @GetMapping("/{photoId}/original-url")
    public ApiResponse<PhotoOriginalUrlResponse> originalUrl(@PathVariable Long activityId, @PathVariable Long photoId) {
        return ApiResponse.success(photoService.originalUrl(activityId, photoId));
    }

    @PostMapping("/{photoId}/like")
    public ApiResponse<Void> like(@PathVariable Long activityId, @PathVariable Long photoId) {
        photoService.like(activityId, photoId);
        return ApiResponse.success(null);
    }

    @DeleteMapping("/{photoId}/like")
    public ApiResponse<Void> unlike(@PathVariable Long activityId, @PathVariable Long photoId) {
        photoService.unlike(activityId, photoId);
        return ApiResponse.success(null);
    }

    @PostMapping("/{photoId}/reports")
    public ApiResponse<Void> report(@PathVariable Long activityId, @PathVariable Long photoId,
                                    @Valid @RequestBody PhotoReportRequest request) {
        photoService.report(activityId, photoId, request);
        return ApiResponse.success(null);
    }

    @DeleteMapping("/{photoId}")
    public ApiResponse<Void> delete(@PathVariable Long activityId, @PathVariable Long photoId) {
        photoService.delete(activityId, photoId);
        return ApiResponse.success(null);
    }
}
