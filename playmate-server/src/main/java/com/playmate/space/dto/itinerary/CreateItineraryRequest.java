package com.playmate.space.dto.itinerary;

import com.playmate.space.dto.poll.CreatePollRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalTime;

public record CreateItineraryRequest(
        @NotBlank(message = "creationMode 不能为空") String creationMode,
        @NotBlank(message = "标题不能为空") @Size(max = 128, message = "标题长度不能超过 128") String title,
        @Size(max = 32, message = "行程类型长度不能超过 32") String itineraryType,
        @NotNull(message = "行程日期不能为空") LocalDate itineraryDate,
        LocalTime startTime,
        LocalTime endTime,
        Boolean allDay,
        @Size(max = 64, message = "交通方式长度不能超过 64") String transportMode,
        @Size(max = 128, message = "出发地长度不能超过 128") String departureName,
        @Size(max = 128, message = "目的地长度不能超过 128") String destinationName,
        @Size(max = 512, message = "路线长度不能超过 512") String routeDetail,
        @Size(max = 64, message = "用餐类型长度不能超过 64") String mealType,
        @Size(max = 128, message = "餐厅名称长度不能超过 128") String restaurantName,
        @Size(max = 128, message = "活动内容长度不能超过 128") String activityContent,
        @Size(max = 128, message = "地点名称长度不能超过 128") String locationName,
        @Size(max = 255, message = "地址长度不能超过 255") String address,
        @Size(max = 2000, message = "说明长度不能超过 2000") String description,
        @Valid CreatePollRequest poll
) {}
