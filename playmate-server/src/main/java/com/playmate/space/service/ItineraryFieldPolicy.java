package com.playmate.space.service;

import com.playmate.space.common.ErrorCode;
import com.playmate.space.common.exception.BusinessException;
import com.playmate.space.dto.poll.PollFieldChangeResponse;
import com.playmate.space.dto.poll.PollUnchangedFieldResponse;
import com.playmate.space.entity.ActivityItineraryEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Component
public class ItineraryFieldPolicy {
    private static final List<String> SNAPSHOT_FIELDS = List.of(
            "title", "itineraryDate", "startTime", "endTime", "transportMode",
            "departureName", "destinationName", "routeDetail", "mealType",
            "restaurantName", "address", "activityContent", "locationName"
    );
    private final ItineraryTypePolicy typePolicy;

    public ItineraryFieldPolicy(ItineraryTypePolicy typePolicy) {
        this.typePolicy = typePolicy;
    }

    public List<String> normalizeNewScope(
            String purpose,
            String itineraryType,
            String decisionType,
            List<String> requestedScope
    ) {
        if ("GENERAL".equals(purpose)) return List.of();
        return typePolicy.resolveDecisionScope(itineraryType, decisionType, requestedScope);
    }

    public List<String> normalizeStoredScope(
            String purpose,
            String decisionType,
            List<String> storedScope
    ) {
        if ("GENERAL".equals(purpose)) return List.of();
        List<String> source = storedScope == null || storedScope.isEmpty()
                ? typePolicy.legacyDefaultScope(decisionType)
                : storedScope;
        List<String> normalized = source.stream().distinct().toList();
        for (String field : normalized) {
            if (!SNAPSHOT_FIELDS.contains(field)) {
                throw param("不支持修改行程字段：" + field);
            }
        }
        if (normalized.isEmpty()) throw param("关联行程投票缺少允许修改的字段");
        return normalized;
    }

    public void validatePayload(Map<String, Object> payload, List<String> scope) {
        if (payload == null || payload.isEmpty()) return;
        Set<String> allowed = Set.copyOf(scope);
        for (Map.Entry<String, Object> entry : payload.entrySet()) {
            String field = entry.getKey();
            if (!SNAPSHOT_FIELDS.contains(field)) throw param("投票选项包含不支持的行程字段：" + field);
            if (!allowed.contains(field)) throw param("投票选项不能修改未授权字段：" + label(field));
            validateValue(field, entry.getValue());
        }
    }

    public void validateTemplate(Map<String, Object> template) {
        if (template == null) return;
        Set<String> allowed = new java.util.LinkedHashSet<>(SNAPSHOT_FIELDS);
        allowed.addAll(List.of("itineraryType", "allDay", "description"));
        for (Map.Entry<String, Object> entry : template.entrySet()) {
            if (!allowed.contains(entry.getKey())) throw param("行程模板包含不支持字段：" + entry.getKey());
            if (SNAPSHOT_FIELDS.contains(entry.getKey())) validateValue(entry.getKey(), entry.getValue());
        }
    }

    public Map<String, Object> snapshot(ActivityItineraryEntity itinerary) {
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        values.put("title", itinerary.getTitle());
        values.put("itineraryDate", text(itinerary.getItineraryDate()));
        values.put("startTime", text(itinerary.getStartTime()));
        values.put("endTime", text(itinerary.getEndTime()));
        values.put("transportMode", itinerary.getTransportMode());
        values.put("departureName", itinerary.getDepartureName());
        values.put("destinationName", itinerary.getDestinationName());
        values.put("routeDetail", itinerary.getRouteDetail());
        values.put("mealType", itinerary.getMealType());
        values.put("restaurantName", itinerary.getRestaurantName());
        values.put("address", itinerary.getAddress());
        values.put("activityContent", itinerary.getActivityContent());
        values.put("locationName", itinerary.getLocationName());
        return values;
    }

    public ActivityItineraryEntity copy(ActivityItineraryEntity source) {
        ActivityItineraryEntity target = new ActivityItineraryEntity();
        target.setId(source.getId());
        target.setActivityId(source.getActivityId());
        target.setTitle(source.getTitle());
        target.setItineraryType(source.getItineraryType());
        target.setItineraryDate(source.getItineraryDate());
        target.setStartTime(source.getStartTime());
        target.setEndTime(source.getEndTime());
        target.setAllDay(source.getAllDay());
        target.setTransportMode(source.getTransportMode());
        target.setDepartureName(source.getDepartureName());
        target.setDestinationName(source.getDestinationName());
        target.setRouteDetail(source.getRouteDetail());
        target.setMealType(source.getMealType());
        target.setRestaurantName(source.getRestaurantName());
        target.setActivityContent(source.getActivityContent());
        target.setLocationName(source.getLocationName());
        target.setAddress(source.getAddress());
        target.setDescription(source.getDescription());
        target.setPlanningStatus(source.getPlanningStatus());
        target.setOriginType(source.getOriginType());
        target.setOriginPollId(source.getOriginPollId());
        target.setCreatedBy(source.getCreatedBy());
        target.setVersion(source.getVersion());
        return target;
    }

    public void apply(ActivityItineraryEntity itinerary, Map<String, Object> payload, List<String> scope) {
        validatePayload(payload, scope);
        for (String field : scope) {
            if (!payload.containsKey(field)) continue;
            Object value = payload.get(field);
            switch (field) {
                case "title" -> itinerary.setTitle(requiredString(value, field));
                case "itineraryDate" -> itinerary.setItineraryDate(date(value, field));
                case "startTime" -> itinerary.setStartTime(time(value, field));
                case "endTime" -> itinerary.setEndTime(time(value, field));
                case "transportMode" -> itinerary.setTransportMode(string(value));
                case "departureName" -> itinerary.setDepartureName(string(value));
                case "destinationName" -> itinerary.setDestinationName(string(value));
                case "routeDetail" -> itinerary.setRouteDetail(string(value));
                case "mealType" -> itinerary.setMealType(string(value));
                case "restaurantName" -> itinerary.setRestaurantName(string(value));
                case "address" -> itinerary.setAddress(string(value));
                case "activityContent" -> itinerary.setActivityContent(string(value));
                case "locationName" -> itinerary.setLocationName(string(value));
                default -> throw param("不支持修改行程字段：" + field);
            }
        }
        typePolicy.validateTimes(
                itinerary.getItineraryType(),
                itinerary.getStartTime(),
                itinerary.getEndTime(),
                itinerary.getAllDay());
    }

    public ChangeSet changes(Map<String, Object> before, Map<String, Object> after, List<String> scope) {
        List<PollFieldChangeResponse> changed = new ArrayList<>();
        List<PollUnchangedFieldResponse> unchanged = new ArrayList<>();
        Set<String> scoped = Set.copyOf(scope);
        for (String field : SNAPSHOT_FIELDS) {
            Object oldValue = before.get(field);
            Object newValue = after.get(field);
            if (scoped.contains(field) && !Objects.equals(normalize(oldValue), normalize(newValue))) {
                changed.add(new PollFieldChangeResponse(field, label(field), oldValue, newValue));
            } else if (!scoped.contains(field) && hasValue(oldValue, newValue)) {
                unchanged.add(new PollUnchangedFieldResponse(
                        field, label(field), newValue != null ? newValue : oldValue));
            }
        }
        return new ChangeSet(changed, unchanged);
    }

    public List<String> labels(List<String> fields) {
        return fields.stream().map(this::label).toList();
    }

    public List<String> unchangedLabels(List<String> scope) {
        Set<String> scoped = Set.copyOf(scope);
        return SNAPSHOT_FIELDS.stream().filter(field -> !scoped.contains(field)).map(this::label).toList();
    }

    public String label(String field) {
        return typePolicy.fieldLabel(field);
    }

    public boolean requiresManualReview(List<String> scope) {
        return typePolicy.isLegacySensitiveScope(scope);
    }

    private void validateValue(String field, Object value) {
        if (value == null) return;
        String text = String.valueOf(value).trim();
        int max = switch (field) {
            case "title", "departureName", "destinationName", "restaurantName", "activityContent", "locationName" -> 128;
            case "transportMode", "mealType" -> 64;
            case "routeDetail" -> 512;
            case "address" -> 255;
            default -> 32;
        };
        if (text.length() > max) throw param(label(field) + "长度不能超过 " + max);
        if ("itineraryDate".equals(field)) date(value, field);
        if ("startTime".equals(field) || "endTime".equals(field)) time(value, field);
    }

    private String requiredString(Object value, String field) {
        String result = string(value);
        if (!StringUtils.hasText(result)) throw param(label(field) + "不能为空");
        return result;
    }

    private String string(Object value) {
        if (value == null) return null;
        String result = String.valueOf(value).trim();
        return result.isEmpty() ? null : result;
    }

    private LocalDate date(Object value, String field) {
        String text = string(value);
        if (!StringUtils.hasText(text)) throw param(label(field) + "不能为空");
        try {
            return LocalDate.parse(text);
        } catch (DateTimeParseException ex) {
            throw param(label(field) + "格式不正确");
        }
    }

    private LocalTime time(Object value, String field) {
        String text = string(value);
        if (!StringUtils.hasText(text)) return null;
        try {
            return LocalTime.parse(text);
        } catch (DateTimeParseException ex) {
            throw param(label(field) + "格式不正确");
        }
    }

    private Object normalize(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private boolean hasValue(Object first, Object second) {
        return StringUtils.hasText(first == null ? null : String.valueOf(first))
                || StringUtils.hasText(second == null ? null : String.valueOf(second));
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private BusinessException param(String message) {
        return new BusinessException(ErrorCode.PARAM_ERROR.code(), message);
    }

    public record ChangeSet(
            List<PollFieldChangeResponse> changedFields,
            List<PollUnchangedFieldResponse> unchangedFields
    ) {
    }
}
