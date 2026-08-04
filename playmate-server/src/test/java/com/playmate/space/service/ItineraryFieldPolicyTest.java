package com.playmate.space.service;

import com.playmate.space.common.exception.BusinessException;
import com.playmate.space.entity.ActivityItineraryEntity;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ItineraryFieldPolicyTest {
    private final ItineraryTypePolicy typePolicy = new ItineraryTypePolicy();
    private final ItineraryFieldPolicy policy = new ItineraryFieldPolicy(typePolicy);

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
        assertTrue(error.getMessage().contains("行程标题"));
    }

    @Test
    void defaultScopeIsDerivedFromDecisionType() {
        assertEquals(
                List.of("mealType", "restaurantName", "address"),
                policy.normalizeNewScope("UPDATE_ITINERARY", "MEAL", "RESTAURANT", null));
        assertEquals(
                List.of("departureName", "destinationName", "routeDetail"),
                policy.normalizeStoredScope("UPDATE_ITINERARY", "ROUTE", null));
        assertEquals(List.of(), policy.normalizeNewScope("GENERAL", null, "RESTAURANT", null));
    }

    @Test
    void legacyTitleAndRouteScopesRequireManualReview() {
        assertTrue(policy.requiresManualReview(List.of("title")));
        assertTrue(policy.requiresManualReview(List.of("departureName", "routeDetail")));
        assertFalse(policy.requiresManualReview(List.of("transportMode")));
    }

    @Test
    void fullPlanChangesTitleAndTransportFieldsWithoutChangingType() {
        ActivityItineraryEntity itinerary = transportItinerary();
        itinerary.setItineraryType("TRANSPORT");
        itinerary.setDescription("原备注");
        List<String> scope = typePolicy.resolveDecisionScope("TRANSPORT", "FULL_PLAN", null);
        Map<String, Object> payload = transportFullPlan();

        policy.validateFullPlanPayload(payload, itinerary.getItineraryType(), itinerary.getAllDay());
        policy.apply(itinerary, payload, scope);

        assertEquals("周六早上高铁出发", itinerary.getTitle());
        assertEquals("高铁", itinerary.getTransportMode());
        assertEquals("杭州东站", itinerary.getDepartureName());
        assertEquals("上海虹桥站", itinerary.getDestinationName());
        assertEquals("TRANSPORT", itinerary.getItineraryType());
        assertEquals("提前取票", itinerary.getDescription());
    }

    @Test
    void fullPlanRejectsItineraryTypeMissingFieldsAndBlankTitle() {
        Map<String, Object> withType = transportFullPlan();
        withType.put("itineraryType", "MEAL");
        assertThrows(BusinessException.class, () -> policy.validateFullPlanPayload(
                withType, "TRANSPORT", 0));

        Map<String, Object> missingDescription = transportFullPlan();
        missingDescription.remove("description");
        assertThrows(BusinessException.class, () -> policy.validateFullPlanPayload(
                missingDescription, "TRANSPORT", 0));

        Map<String, Object> blankTitle = transportFullPlan();
        blankTitle.put("title", " ");
        assertThrows(BusinessException.class, () -> policy.validateFullPlanPayload(
                blankTitle, "TRANSPORT", 0));
    }

    @Test
    void fullPlanExplicitNullClearsOptionalFieldsAndTracksDescriptionChange() {
        ActivityItineraryEntity itinerary = transportItinerary();
        itinerary.setItineraryType("TRANSPORT");
        itinerary.setDescription("原备注");
        Map<String, Object> before = policy.snapshot(itinerary);
        Map<String, Object> payload = transportFullPlan();
        payload.put("departureName", null);
        payload.put("description", "");
        List<String> scope = typePolicy.resolveDecisionScope("TRANSPORT", "FULL_PLAN", null);

        policy.validateFullPlanPayload(payload, "TRANSPORT", 0);
        policy.apply(itinerary, payload, scope);
        ItineraryFieldPolicy.ChangeSet changes = policy.changes(before, policy.snapshot(itinerary), scope);

        assertNull(itinerary.getDepartureName());
        assertNull(itinerary.getDescription());
        assertTrue(changes.changedFields().stream().anyMatch(change -> "title".equals(change.field())));
        assertTrue(changes.changedFields().stream().anyMatch(change -> "description".equals(change.field())));
    }

    @Test
    void identicalFullPlanProducesEmptyChangedFields() {
        ActivityItineraryEntity itinerary = transportItinerary();
        itinerary.setItineraryType("TRANSPORT");
        itinerary.setDescription("原备注");
        List<String> scope = typePolicy.resolveDecisionScope("TRANSPORT", "FULL_PLAN", null);
        Map<String, Object> payload = new LinkedHashMap<>(policy.snapshot(itinerary));
        payload.keySet().retainAll(scope);
        Map<String, Object> before = policy.snapshot(itinerary);

        policy.validateFullPlanPayload(payload, "TRANSPORT", 0);
        policy.apply(itinerary, payload, scope);

        assertTrue(policy.changes(before, policy.snapshot(itinerary), scope).changedFields().isEmpty());
    }

    private Map<String, Object> transportFullPlan() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", "周六早上高铁出发");
        payload.put("itineraryDate", "2026-07-19");
        payload.put("startTime", "08:00");
        payload.put("endTime", "10:00");
        payload.put("transportMode", "高铁");
        payload.put("departureName", "杭州东站");
        payload.put("destinationName", "上海虹桥站");
        payload.put("description", "提前取票");
        return payload;
    }

    private ActivityItineraryEntity transportItinerary() {
        ActivityItineraryEntity itinerary = new ActivityItineraryEntity();
        itinerary.setTitle("周日返程");
        itinerary.setItineraryDate(LocalDate.of(2026, 7, 19));
        itinerary.setStartTime(LocalTime.of(9, 0));
        itinerary.setEndTime(LocalTime.of(12, 0));
        itinerary.setAllDay(0);
        itinerary.setTransportMode("高铁");
        itinerary.setDepartureName("亚朵酒店");
        itinerary.setDestinationName("上海");
        return itinerary;
    }
}
