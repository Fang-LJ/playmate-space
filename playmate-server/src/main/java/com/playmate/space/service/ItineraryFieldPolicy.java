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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Component
public class ItineraryFieldPolicy {
    private static final LinkedHashMap<String, String> LABELS = new LinkedHashMap<>();
    private static final Map<String, List<String>> DEFAULT_SCOPES = Map.of(
            "TRANSPORT", List.of("transportMode"),
            "ROUTE", List.of("departureName", "destinationName", "routeDetail"),
            "TIME", List.of("itineraryDate", "startTime", "endTime"),
            "RESTAURANT", List.of("mealType", "restaurantName", "address"),
            "PLACE", List.of("locationName"),
            "CONTENT", List.of("activityContent"),
            "ITINERARY_NAME", List.of("title"),
            "OTHER", List.of("title")
    );

    static {
        LABELS.put("title", "行程名称");
        LABELS.put("itineraryDate", "日期");
        LABELS.put("startTime", "开始时间");
        LABELS.put("endTime", "结束时间");
        LABELS.put("transportMode", "交通方式");
        LABELS.put("departureName", "出发地");
        LABELS.put("destinationName", "目的地");
        LABELS.put("routeDetail", "路线");
        LABELS.put("mealType", "用餐类型");
        LABELS.put("restaurantName", "具体餐厅");
        LABELS.put("address", "详细地址");
        LABELS.put("activityContent", "活动内容");
        LABELS.put("locationName", "地点");
    }

    public List<String> normalizeScope(String purpose, String decisionType, List<String> requestedScope) {
        if ("GENERAL".equals(purpose)) return List.of();
        List<String> source = requestedScope == null || requestedScope.isEmpty()
                ? DEFAULT_SCOPES.getOrDefault(decisionType, List.of())
                : requestedScope;
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String field : source) {
            if (!LABELS.containsKey(field)) throw param("不支持修改行程字段：" + field);
            normalized.add(field);
        }
        if (normalized.isEmpty()) throw param("关联行程投票必须声明允许修改的字段");
        return List.copyOf(normalized);
    }

    public void validatePayload(Map<String, Object> payload, List<String> scope) {
        if (payload == null || payload.isEmpty()) return;
        Set<String> allowed = Set.copyOf(scope);
        for (Map.Entry<String, Object> entry : payload.entrySet()) {
            String field = entry.getKey();
            if (!LABELS.containsKey(field)) throw param("投票选项包含不支持的行程字段：" + field);
            if (!allowed.contains(field)) throw param("投票选项不能修改未授权字段：" + label(field));
            validateValue(field, entry.getValue());
        }
    }

    public void validateTemplate(Map<String, Object> template) {
        if (template == null) return;
        Set<String> allowed = new LinkedHashSet<>(LABELS.keySet());
        allowed.addAll(List.of("itineraryType", "allDay", "description"));
        for (Map.Entry<String, Object> entry : template.entrySet()) {
            if (!allowed.contains(entry.getKey())) throw param("行程模板包含不支持字段：" + entry.getKey());
            if (LABELS.containsKey(entry.getKey())) validateValue(entry.getKey(), entry.getValue());
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
        if (itinerary.getStartTime() != null && itinerary.getEndTime() != null
                && !itinerary.getEndTime().isAfter(itinerary.getStartTime())) {
            throw param("结束时间必须晚于开始时间");
        }
    }

    public ChangeSet changes(Map<String, Object> before, Map<String, Object> after, List<String> scope) {
        List<PollFieldChangeResponse> changed = new ArrayList<>();
        List<PollUnchangedFieldResponse> unchanged = new ArrayList<>();
        Set<String> scoped = Set.copyOf(scope);
        for (Map.Entry<String, String> field : LABELS.entrySet()) {
            Object oldValue = before.get(field.getKey());
            Object newValue = after.get(field.getKey());
            if (scoped.contains(field.getKey()) && !Objects.equals(normalize(oldValue), normalize(newValue))) {
                changed.add(new PollFieldChangeResponse(field.getKey(), field.getValue(), oldValue, newValue));
            } else if (!scoped.contains(field.getKey()) && hasValue(oldValue, newValue)) {
                unchanged.add(new PollUnchangedFieldResponse(
                        field.getKey(), field.getValue(), newValue != null ? newValue : oldValue));
            }
        }
        return new ChangeSet(changed, unchanged);
    }

    public List<String> labels(List<String> fields) {
        return fields.stream().map(this::label).toList();
    }

    public List<String> unchangedLabels(List<String> scope) {
        Set<String> scoped = Set.copyOf(scope);
        return LABELS.keySet().stream().filter(field -> !scoped.contains(field)).map(this::label).toList();
    }

    public String label(String field) {
        return LABELS.getOrDefault(field, field);
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
