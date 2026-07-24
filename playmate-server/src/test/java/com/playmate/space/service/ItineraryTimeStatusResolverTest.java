package com.playmate.space.service;

import com.playmate.space.entity.ActivityItineraryEntity;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ItineraryTimeStatusResolverTest {
    @Test void resolvesTimedItinerary() {
        ActivityItineraryEntity itinerary = itinerary(LocalDate.of(2026, 7, 12), LocalTime.of(10, 0), LocalTime.of(12, 0), 0);
        assertEquals("UPCOMING", ItineraryTimeStatusResolver.resolve(itinerary, LocalDateTime.of(2026, 7, 12, 9, 0)));
        assertEquals("IN_PROGRESS", ItineraryTimeStatusResolver.resolve(itinerary, LocalDateTime.of(2026, 7, 12, 11, 0)));
        assertEquals("FINISHED", ItineraryTimeStatusResolver.resolve(itinerary, LocalDateTime.of(2026, 7, 12, 13, 0)));
    }
    @Test void resolvesAllDayAndCanceledItinerary() {
        ActivityItineraryEntity itinerary = itinerary(LocalDate.of(2026, 7, 12), null, null, 1);
        assertEquals("IN_PROGRESS", ItineraryTimeStatusResolver.resolve(itinerary, LocalDateTime.of(2026, 7, 12, 8, 0)));
        itinerary.setPlanningStatus("CANCELED");
        assertEquals("CANCELED", ItineraryTimeStatusResolver.resolve(itinerary, LocalDateTime.now()));
    }
    @Test void resolvesLodgingAcrossMidnight() {
        ActivityItineraryEntity itinerary = itinerary(
                LocalDate.of(2026, 7, 12), LocalTime.of(21, 0), LocalTime.of(9, 0), 0);
        itinerary.setItineraryType("LODGING");
        assertEquals("IN_PROGRESS", ItineraryTimeStatusResolver.resolve(
                itinerary, LocalDateTime.of(2026, 7, 13, 8, 0)));
        assertEquals("FINISHED", ItineraryTimeStatusResolver.resolve(
                itinerary, LocalDateTime.of(2026, 7, 13, 10, 0)));
    }
    private ActivityItineraryEntity itinerary(LocalDate date, LocalTime start, LocalTime end, int allDay) { ActivityItineraryEntity i=new ActivityItineraryEntity();i.setItineraryDate(date);i.setStartTime(start);i.setEndTime(end);i.setAllDay(allDay);i.setPlanningStatus("CONFIRMED");i.setItineraryType("OTHER");return i; }
}
