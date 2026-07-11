package com.playmate.space.dto.itinerary;

import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalTime;

public record UpdateItineraryRequest(
        @Size(max = 128, message = "标题长度不能超过 128") String title,
        @Size(max = 32, message = "行程类型长度不能超过 32") String itineraryType,
        LocalDate itineraryDate,
        LocalTime startTime,
        LocalTime endTime,
        Boolean allDay,
        @Size(max = 128, message = "地点名称长度不能超过 128") String locationName,
        @Size(max = 255, message = "地址长度不能超过 255") String address,
        @Size(max = 2000, message = "说明长度不能超过 2000") String description
) {}
