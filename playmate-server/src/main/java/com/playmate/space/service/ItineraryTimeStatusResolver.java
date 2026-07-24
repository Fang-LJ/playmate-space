package com.playmate.space.service;

import com.playmate.space.entity.ActivityItineraryEntity;
import java.time.LocalDate;
import java.time.LocalDateTime;

public final class ItineraryTimeStatusResolver {
    private ItineraryTimeStatusResolver() {}
    public static String resolve(ActivityItineraryEntity itinerary, LocalDateTime now) {
        if ("CANCELED".equals(itinerary.getPlanningStatus())) return "CANCELED";
        LocalDate date = itinerary.getItineraryDate();
        if (date == null) return "UPCOMING";
        if (itinerary.getAllDay() != null && itinerary.getAllDay() == 1) {
            if (now.toLocalDate().isBefore(date)) return "UPCOMING";
            return now.toLocalDate().isAfter(date) ? "FINISHED" : "IN_PROGRESS";
        }
        LocalDateTime start = itinerary.getStartTime() == null ? date.atStartOfDay() : date.atTime(itinerary.getStartTime());
        LocalDateTime end = itinerary.getEndTime() == null ? date.plusDays(1).atStartOfDay() : date.atTime(itinerary.getEndTime());
        if ("LODGING".equals(itinerary.getItineraryType())
                && itinerary.getStartTime() != null
                && itinerary.getEndTime() != null
                && !itinerary.getEndTime().isAfter(itinerary.getStartTime())) {
            end = date.plusDays(1).atTime(itinerary.getEndTime());
        }
        if (now.isBefore(start)) return "UPCOMING";
        return now.isBefore(end) ? "IN_PROGRESS" : "FINISHED";
    }
}
