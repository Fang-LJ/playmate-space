package com.playmate.space.service;

import com.playmate.space.common.exception.BusinessException;
import com.playmate.space.entity.ActivityItineraryEntity;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ItineraryFieldPolicyTest {
    private final ItineraryFieldPolicy policy = new ItineraryFieldPolicy();

    @Test
    void transportVoteOnlyChangesTransportMode() {
        ActivityItineraryEntity itinerary = transportItinerary();
        Map<String, Object> before = policy.snapshot(itinerary);

        policy.apply(itinerary, Map.of("transportMode", "自驾"), List.of("transportMode"));

        assertEquals("周日返程", itinerary.getTitle());
        assertEquals(LocalDate.of(2026, 7, 19), itinerary.getItineraryDate());
        assertEquals(LocalTime.of(9, 0), itinerary.getStartTime());
        assertEquals("亚朵酒店", itinerary.getDepartureName());
        assertEquals("上海", itinerary.getDestinationName());
        assertEquals("自驾", itinerary.getTransportMode());
        assertEquals(1, policy.changes(before, policy.snapshot(itinerary), List.of("transportMode"))
                .changedFields().size());
    }

    @Test
    void restaurantVoteOnlyChangesRestaurantFields() {
        ActivityItineraryEntity itinerary = new ActivityItineraryEntity();
        itinerary.setTitle("周日晚餐");
        itinerary.setItineraryDate(LocalDate.of(2026, 7, 19));
        itinerary.setStartTime(LocalTime.of(18, 0));
        itinerary.setEndTime(LocalTime.of(20, 0));
        Map<String, Object> before = policy.snapshot(itinerary);

        List<String> scope = List.of("mealType", "restaurantName", "address");
        policy.apply(itinerary, Map.of(
                "mealType", "火锅",
                "restaurantName", "海底捞湖滨店",
                "address", "湖滨路 88 号"), scope);

        assertEquals("周日晚餐", itinerary.getTitle());
        assertEquals(LocalTime.of(18, 0), itinerary.getStartTime());
        assertEquals("火锅", itinerary.getMealType());
        assertEquals("海底捞湖滨店", itinerary.getRestaurantName());
        assertEquals(3, policy.changes(before, policy.snapshot(itinerary), scope).changedFields().size());
    }

    @Test
    void rejectsFieldOutsideDecisionScope() {
        BusinessException error = assertThrows(
                BusinessException.class,
                () -> policy.validatePayload(
                        Map.of("transportMode", "自驾", "title", "自驾"),
                        List.of("transportMode")));
        assertTrue(error.getMessage().contains("行程名称"));
    }

    @Test
    void defaultScopeIsDerivedFromDecisionType() {
        assertEquals(
                List.of("mealType", "restaurantName", "address"),
                policy.normalizeScope("UPDATE_ITINERARY", "RESTAURANT", null));
        assertEquals(List.of(), policy.normalizeScope("GENERAL", "RESTAURANT", null));
    }

    private ActivityItineraryEntity transportItinerary() {
        ActivityItineraryEntity itinerary = new ActivityItineraryEntity();
        itinerary.setTitle("周日返程");
        itinerary.setItineraryDate(LocalDate.of(2026, 7, 19));
        itinerary.setStartTime(LocalTime.of(9, 0));
        itinerary.setEndTime(LocalTime.of(12, 0));
        itinerary.setTransportMode("高铁");
        itinerary.setDepartureName("亚朵酒店");
        itinerary.setDestinationName("上海");
        return itinerary;
    }
}
