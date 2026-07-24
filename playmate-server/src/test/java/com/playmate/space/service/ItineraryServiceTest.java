package com.playmate.space.service;

import com.playmate.space.dto.itinerary.UpdateItineraryRequest;
import com.playmate.space.entity.ActivityEntity;
import com.playmate.space.entity.ActivityItineraryEntity;
import com.playmate.space.entity.ActivityMemberEntity;
import com.playmate.space.mapper.ActivityItineraryMapper;
import com.playmate.space.mapper.ActivityPollMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ItineraryServiceTest {
    private static final Long ACTIVITY_ID = 10L;
    private static final Long USER_ID = 20L;

    @Mock
    private ActivityCollaborationAccess access;
    @Mock
    private ActivityItineraryMapper itineraryMapper;
    @Mock
    private PollService pollService;
    @Mock
    private ActivityPollMapper pollMapper;

    private ItineraryTypePolicy typePolicy;
    private ItineraryService service;

    @BeforeEach
    void setUp() {
        typePolicy = spy(new ItineraryTypePolicy());
        service = new ItineraryService(
                access, itineraryMapper, pollService, pollMapper, typePolicy);

        ActivityEntity activity = new ActivityEntity();
        activity.setId(ACTIVITY_ID);
        activity.setCreatorUserId(USER_ID);
        activity.setStatus("PLANNING");
        ActivityMemberEntity member = new ActivityMemberEntity();
        member.setActivityId(ACTIVITY_ID);
        member.setUserId(USER_ID);
        member.setRole("CREATOR");
        member.setMemberStatus("ACTIVE");

        when(access.requireUserId()).thenReturn(USER_ID);
        when(access.requireActivity(ACTIVITY_ID)).thenReturn(activity);
        when(access.requireActiveMember(ACTIVITY_ID, USER_ID)).thenReturn(member);
    }

    @Test
    void sameTypeEditClearsDirtyFieldsAndPreservesAllowedFields() {
        ActivityItineraryEntity meal = itinerary(1L, "MEAL");
        meal.setMealType("火锅");
        meal.setRestaurantName("海底捞");
        meal.setAddress("湖滨路 1 号");
        meal.setTransportMode("自驾");
        when(itineraryMapper.selectById(1L)).thenReturn(meal);

        service.update(ACTIVITY_ID, 1L, request("新的用餐标题", null, null));

        assertEquals("新的用餐标题", meal.getTitle());
        assertEquals("火锅", meal.getMealType());
        assertEquals("海底捞", meal.getRestaurantName());
        assertEquals("湖滨路 1 号", meal.getAddress());
        assertNull(meal.getTransportMode());
        verify(typePolicy).validatePersistedFields(meal);
    }

    @Test
    void sameTypeEditDoesNotClearLegalTransportFields() {
        ActivityItineraryEntity transport = itinerary(2L, "TRANSPORT");
        transport.setTransportMode("自驾");
        transport.setDepartureName("杭州");
        transport.setDestinationName("桐庐");
        when(itineraryMapper.selectById(2L)).thenReturn(transport);

        service.update(ACTIVITY_ID, 2L, request(null, null, "更新后的备注"));

        assertEquals("自驾", transport.getTransportMode());
        assertEquals("杭州", transport.getDepartureName());
        assertEquals("桐庐", transport.getDestinationName());
        assertEquals("更新后的备注", transport.getDescription());
        verify(typePolicy).validatePersistedFields(transport);
    }

    @Test
    void ordinaryEditMigratesLegacyRouteOnlyOnce() {
        ActivityItineraryEntity transport = itinerary(3L, "TRANSPORT");
        transport.setDescription("带好证件");
        transport.setRouteDetail("沿江高速集合");
        when(itineraryMapper.selectById(3L)).thenReturn(transport);

        service.update(ACTIVITY_ID, 3L, new UpdateItineraryRequest(
                null, null, null, null, null, null,
                null, null, null, "沿江高速集合",
                null, null, null, null, null, null));
        service.update(ACTIVITY_ID, 3L, emptyRequest());

        assertNull(transport.getRouteDetail());
        assertEquals("带好证件\n沿江高速集合", transport.getDescription());
        assertEquals(1, occurrences(transport.getDescription(), "沿江高速集合"));
        verify(typePolicy, times(2)).validatePersistedFields(transport);
    }

    @Test
    void typeSwitchClearsOldSemanticsAndPreservesCommonFields() {
        ActivityItineraryEntity transport = itinerary(4L, "TRANSPORT");
        transport.setTransportMode("自驾");
        transport.setDepartureName("杭州");
        transport.setDestinationName("桐庐");
        transport.setDescription("周末出行");
        when(itineraryMapper.selectById(4L)).thenReturn(transport);

        service.update(ACTIVITY_ID, 4L, new UpdateItineraryRequest(
                null, "MEAL", null, null, null, null,
                null, null, null, null,
                "火锅", "海底捞", null, null, "湖滨路 1 号", null));

        assertEquals("MEAL", transport.getItineraryType());
        assertNull(transport.getTransportMode());
        assertNull(transport.getDepartureName());
        assertNull(transport.getDestinationName());
        assertEquals("火锅", transport.getMealType());
        assertEquals("海底捞", transport.getRestaurantName());
        assertEquals("周末安排", transport.getTitle());
        assertEquals(LocalDate.of(2026, 7, 25), transport.getItineraryDate());
        assertEquals("周末出行", transport.getDescription());

        ActivityItineraryEntity lodging = itinerary(5L, "LODGING");
        lodging.setLocationName("富阳亚朵酒店");
        lodging.setAddress("幸福路 88 号");
        when(itineraryMapper.selectById(5L)).thenReturn(lodging);

        service.update(ACTIVITY_ID, 5L, new UpdateItineraryRequest(
                null, "ACTIVITY", null, null, null, null,
                null, null, null, null,
                null, null, "桌游", null, null, null));

        assertEquals("ACTIVITY", lodging.getItineraryType());
        assertNull(lodging.getLocationName());
        assertNull(lodging.getAddress());
        assertEquals("桌游", lodging.getActivityContent());
        verify(typePolicy, times(2)).validatePersistedFields(any());
    }

    private ActivityItineraryEntity itinerary(Long id, String type) {
        ActivityItineraryEntity itinerary = new ActivityItineraryEntity();
        itinerary.setId(id);
        itinerary.setActivityId(ACTIVITY_ID);
        itinerary.setTitle("周末安排");
        itinerary.setItineraryType(type);
        itinerary.setItineraryDate(LocalDate.of(2026, 7, 25));
        itinerary.setStartTime(LocalTime.of(10, 0));
        itinerary.setEndTime(LocalTime.of(12, 0));
        itinerary.setAllDay(0);
        itinerary.setPlanningStatus("CONFIRMED");
        itinerary.setOriginType("MANUAL");
        itinerary.setCreatedBy(USER_ID);
        itinerary.setVersion(0);
        itinerary.setDeleteFlag(0);
        return itinerary;
    }

    private UpdateItineraryRequest request(
            String title,
            String type,
            String description
    ) {
        return new UpdateItineraryRequest(
                title, type, null, null, null, null,
                null, null, null, null,
                null, null, null, null, null, description);
    }

    private UpdateItineraryRequest emptyRequest() {
        return request(null, null, null);
    }

    private int occurrences(String source, String target) {
        int count = 0;
        int index = 0;
        while ((index = source.indexOf(target, index)) >= 0) {
            count++;
            index += target.length();
        }
        return count;
    }
}
