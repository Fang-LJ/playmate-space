package com.playmate.space.service;

import com.playmate.space.common.exception.BusinessException;
import com.playmate.space.dto.itinerary.ItineraryTypeMetadataResponse;
import com.playmate.space.entity.ActivityItineraryEntity;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItineraryTypePolicyTest {
    private final ItineraryTypePolicy policy = new ItineraryTypePolicy();

    @Test
    void exposesAllSixTypesInStableOrderWithExpectedFields() {
        List<ItineraryTypeMetadataResponse> metadata = policy.metadata();

        assertEquals(
                List.of("TRANSPORT", "MEAL", "LODGING", "SIGHTSEEING", "ACTIVITY", "OTHER"),
                metadata.stream().map(ItineraryTypeMetadataResponse::type).toList());
        assertEquals(
                List.of("transportMode", "departureName", "destinationName"),
                keys(metadata.get(0).focusFields()));
        assertEquals(
                List.of("title", "itineraryDate", "startTime", "endTime", "description"),
                keys(metadata.get(0).commonFields()));
        assertEquals(
                List.of("locationName", "startTime", "endTime"),
                keys(metadata.get(2).focusFields()));
        assertEquals(
                List.of("title", "itineraryDate", "address", "description"),
                keys(metadata.get(2).commonFields()));
        assertEquals(List.of("locationName"), keys(metadata.get(5).focusFields()));
        assertThrows(BusinessException.class, () -> policy.normalizeType("UNKNOWN"));
    }

    @Test
    void rejectsNonEmptyFieldsFromOtherTypesButAcceptsBlankValues() {
        assertThrows(BusinessException.class, () -> policy.validateRequestedFields(
                "TRANSPORT", Map.of("restaurantName", "海底捞")));
        assertThrows(BusinessException.class, () -> policy.validateRequestedFields(
                "MEAL", Map.of("transportMode", "自驾")));
        assertThrows(BusinessException.class, () -> policy.validateRequestedFields(
                "OTHER", Map.of("mealType", "火锅")));

        policy.validateRequestedFields(
                "TRANSPORT", Map.of("restaurantName", " ", "transportMode", "自驾"));
    }

    @Test
    void clearsOldTypeFieldsAndPreservesCommonFieldsDuringTypeSwitch() {
        ActivityItineraryEntity itinerary = base("TRANSPORT");
        itinerary.setTransportMode("自驾");
        itinerary.setDepartureName("杭州");
        itinerary.setDestinationName("桐庐");
        itinerary.setRouteDetail("高速集合");
        itinerary.setDescription("带好证件");

        String description = policy.mergeLegacyRouteDetail(
                itinerary.getDescription(), itinerary.getRouteDetail());
        policy.clearTypeSpecificFields(itinerary);
        itinerary.setItineraryType("MEAL");
        itinerary.setMealType("火锅");
        itinerary.setRestaurantName("江边火锅");
        itinerary.setAddress("滨江路 1 号");
        itinerary.setDescription(description);
        policy.validatePersistedFields(itinerary);

        assertEquals("周末安排", itinerary.getTitle());
        assertEquals(LocalDate.of(2026, 7, 25), itinerary.getItineraryDate());
        assertNull(itinerary.getTransportMode());
        assertNull(itinerary.getDepartureName());
        assertNull(itinerary.getDestinationName());
        assertNull(itinerary.getRouteDetail());
        assertEquals("火锅", itinerary.getMealType());
        assertTrue(itinerary.getDescription().contains("高速集合"));
    }

    @Test
    void clearsSameNamedLocationWhenLodgingChangesToActivity() {
        ActivityItineraryEntity itinerary = base("LODGING");
        itinerary.setLocationName("富阳亚朵酒店");
        itinerary.setAddress("幸福路 88 号");

        policy.clearTypeSpecificFields(itinerary);
        itinerary.setItineraryType("ACTIVITY");
        assertNull(itinerary.getLocationName());
        assertNull(itinerary.getAddress());

        itinerary.setActivityContent("桌游");
        itinerary.setLocationName("酒店桌游区");
        policy.validatePersistedFields(itinerary);
        assertEquals("酒店桌游区", itinerary.getLocationName());
    }

    @Test
    void generatesSummaryForEveryType() {
        ActivityItineraryEntity transport = base("TRANSPORT");
        transport.setTransportMode("自驾");
        transport.setDepartureName("亚朵酒店");
        transport.setDestinationName("仁文公寓");
        assertEquals("自驾 · 亚朵酒店 → 仁文公寓", policy.displaySummary(transport));

        ActivityItineraryEntity meal = base("MEAL");
        meal.setMealType("火锅");
        meal.setRestaurantName("海底捞湖滨店");
        assertEquals("火锅 · 海底捞湖滨店", policy.displaySummary(meal));

        ActivityItineraryEntity lodging = base("LODGING");
        lodging.setLocationName("富阳亚朵酒店");
        lodging.setAddress("幸福路 88 号");
        assertEquals("富阳亚朵酒店 · 幸福路 88 号", policy.displaySummary(lodging));

        ActivityItineraryEntity sightseeing = base("SIGHTSEEING");
        sightseeing.setActivityContent("漂流");
        sightseeing.setLocationName("OMG 心跳乐园");
        assertEquals("漂流 · OMG 心跳乐园", policy.displaySummary(sightseeing));

        ActivityItineraryEntity activity = base("ACTIVITY");
        activity.setActivityContent("桌游");
        activity.setLocationName("亚朵酒店桌游区");
        assertEquals("桌游 · 亚朵酒店桌游区", policy.displaySummary(activity));

        ActivityItineraryEntity other = base("OTHER");
        other.setLocationName("酒店大堂");
        assertEquals("酒店大堂", policy.displaySummary(other));
        other.setLocationName(null);
        assertEquals("其他安排", policy.displaySummary(other));
    }

    @Test
    void derivesStrictDecisionScopesAndAllowsOnlyNarrowing() {
        assertEquals(
                List.of("transportMode"),
                policy.resolveDecisionScope("TRANSPORT", "TRANSPORT", null));
        assertEquals(
                List.of("departureName", "destinationName"),
                policy.resolveDecisionScope("TRANSPORT", "ROUTE", null));
        assertEquals(
                List.of("departureName"),
                policy.resolveDecisionScope(
                        "TRANSPORT", "ROUTE", List.of("departureName")));
        assertEquals(
                List.of("mealType", "restaurantName", "address"),
                policy.resolveDecisionScope("MEAL", "RESTAURANT", null));
        assertEquals(
                List.of("locationName", "address"),
                policy.resolveDecisionScope("LODGING", "PLACE", null));

        assertThrows(BusinessException.class, () -> policy.resolveDecisionScope(
                "TRANSPORT", "ROUTE", List.of("departureName", "routeDetail")));
        assertThrows(BusinessException.class, () -> policy.resolveDecisionScope(
                "MEAL", "TRANSPORT", null));
        assertThrows(BusinessException.class, () -> policy.resolveDecisionScope(
                "TRANSPORT", "RESTAURANT", null));
        assertThrows(BusinessException.class, () -> policy.resolveDecisionScope(
                "LODGING", "CONTENT", null));
        assertThrows(BusinessException.class, () -> policy.resolveDecisionScope(
                "OTHER", "ITINERARY_NAME", List.of("title")));
    }

    @Test
    void validatesOrdinaryAndOvernightLodgingTimes() {
        assertThrows(BusinessException.class, () -> policy.validateTimes(
                "ACTIVITY", LocalTime.of(21, 0), LocalTime.of(9, 0), 0));
        policy.validateTimes(
                "LODGING", LocalTime.of(21, 0), LocalTime.of(9, 0), 0);
        policy.validateTimes(
                "LODGING", LocalTime.of(21, 0), LocalTime.of(21, 0), 0);
        policy.validateTimes(
                "ACTIVITY", LocalTime.of(21, 0), LocalTime.of(9, 0), 1);
    }

    @Test
    void keepsLegacyRouteReadableWithoutTreatingItAsNewCoreField() {
        ActivityItineraryEntity itinerary = base("TRANSPORT");
        itinerary.setRouteDetail("酒店集合后出发");
        assertEquals("酒店集合后出发", policy.effectiveDescription(itinerary));

        String merged = policy.mergeLegacyRouteDetail("带好证件", itinerary.getRouteDetail());
        assertEquals("带好证件\n酒店集合后出发", merged);
        assertFalse(policy.legacyDefaultScope("ROUTE").isEmpty());
        assertTrue(policy.isLegacySensitiveScope(List.of("routeDetail")));
    }

    private ActivityItineraryEntity base(String type) {
        ActivityItineraryEntity itinerary = new ActivityItineraryEntity();
        itinerary.setTitle("周末安排");
        itinerary.setItineraryType(type);
        itinerary.setItineraryDate(LocalDate.of(2026, 7, 25));
        itinerary.setStartTime(LocalTime.of(10, 0));
        itinerary.setEndTime(LocalTime.of(12, 0));
        itinerary.setAllDay(0);
        itinerary.setPlanningStatus("CONFIRMED");
        return itinerary;
    }

    private List<String> keys(
            List<com.playmate.space.dto.itinerary.ItineraryFieldMetadata> fields
    ) {
        return fields.stream().map(
                com.playmate.space.dto.itinerary.ItineraryFieldMetadata::key).toList();
    }
}
