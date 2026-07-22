package com.playmate.space.dto.itinerary;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public record ItineraryResponse(
        Long itineraryId, Long activityId, String title, String itineraryType, LocalDate itineraryDate,
        LocalTime startTime, LocalTime endTime, Boolean allDay, String transportMode, String departureName,
        String destinationName, String routeDetail, String mealType, String restaurantName, String activityContent,
        String locationName, String address, String description, String displaySummary, String planningStatus,
        String timeStatus, String originType, Long originPollId,
        Long createdBy, Integer version, LocalDateTime createTime, LocalDateTime updateTime
) {}
