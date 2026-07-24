package com.playmate.space.service;

import com.playmate.space.common.ErrorCode;
import com.playmate.space.common.exception.BusinessException;
import com.playmate.space.dto.itinerary.ItineraryFieldMetadata;
import com.playmate.space.dto.itinerary.ItineraryTypeMetadataResponse;
import com.playmate.space.entity.ActivityItineraryEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class ItineraryTypePolicy {
    private static final List<String> TYPE_ORDER = List.of(
            "TRANSPORT", "MEAL", "LODGING", "SIGHTSEEING", "ACTIVITY", "OTHER");

    private final LinkedHashMap<String, String> fieldLabels = new LinkedHashMap<>();
    private final LinkedHashMap<String, TypeDefinition> definitions = new LinkedHashMap<>();

    public ItineraryTypePolicy() {
        fieldLabels.put("title", "行程标题");
        fieldLabels.put("itineraryDate", "日期");
        fieldLabels.put("startTime", "开始时间");
        fieldLabels.put("endTime", "结束时间");
        fieldLabels.put("description", "备注说明");
        fieldLabels.put("transportMode", "交通方式");
        fieldLabels.put("departureName", "出发地");
        fieldLabels.put("destinationName", "目的地");
        fieldLabels.put("mealType", "用餐类型");
        fieldLabels.put("restaurantName", "具体餐厅");
        fieldLabels.put("activityContent", "活动内容");
        fieldLabels.put("locationName", "地点");
        fieldLabels.put("address", "详细地址");
        fieldLabels.put("routeDetail", "历史路线说明");

        add("TRANSPORT", "交通",
                fields(
                        "transportMode", "交通方式",
                        "departureName", "出发地",
                        "destinationName", "目的地"),
                fields(
                        "title", "行程标题",
                        "itineraryDate", "日期",
                        "startTime", "开始时间",
                        "endTime", "结束时间",
                        "description", "备注说明"),
                decisions(
                        "TRANSPORT", List.of("transportMode"),
                        "ROUTE", List.of("departureName", "destinationName"),
                        "TIME", List.of("itineraryDate", "startTime", "endTime")));
        add("MEAL", "用餐",
                fields(
                        "mealType", "用餐类型",
                        "restaurantName", "具体餐厅"),
                fields(
                        "title", "行程标题",
                        "itineraryDate", "日期",
                        "startTime", "开始时间",
                        "endTime", "结束时间",
                        "address", "详细地址",
                        "description", "备注说明"),
                decisions(
                        "RESTAURANT", List.of("mealType", "restaurantName", "address"),
                        "TIME", List.of("itineraryDate", "startTime", "endTime")));
        add("LODGING", "住宿",
                fields(
                        "locationName", "酒店名称",
                        "startTime", "入住时间",
                        "endTime", "离开时间"),
                fields(
                        "title", "行程标题",
                        "itineraryDate", "日期",
                        "address", "详细地址",
                        "description", "备注说明"),
                decisions(
                        "PLACE", List.of("locationName", "address"),
                        "TIME", List.of("itineraryDate", "startTime", "endTime")));
        add("SIGHTSEEING", "景点",
                fields(
                        "activityContent", "游玩内容",
                        "locationName", "景点名称"),
                fields(
                        "title", "行程标题",
                        "itineraryDate", "日期",
                        "startTime", "开始时间",
                        "endTime", "结束时间",
                        "address", "详细地址",
                        "description", "备注说明"),
                decisions(
                        "CONTENT", List.of("activityContent"),
                        "PLACE", List.of("locationName", "address"),
                        "TIME", List.of("itineraryDate", "startTime", "endTime")));
        add("ACTIVITY", "活动",
                fields(
                        "activityContent", "活动内容",
                        "locationName", "活动地点"),
                fields(
                        "title", "行程标题",
                        "itineraryDate", "日期",
                        "startTime", "开始时间",
                        "endTime", "结束时间",
                        "address", "详细地址",
                        "description", "备注说明"),
                decisions(
                        "CONTENT", List.of("activityContent"),
                        "PLACE", List.of("locationName", "address"),
                        "TIME", List.of("itineraryDate", "startTime", "endTime")));
        add("OTHER", "其他",
                fields("locationName", "地点"),
                fields(
                        "title", "行程标题",
                        "itineraryDate", "日期",
                        "startTime", "开始时间",
                        "endTime", "结束时间",
                        "address", "详细地址",
                        "description", "备注说明"),
                decisions(
                        "PLACE", List.of("locationName", "address"),
                        "TIME", List.of("itineraryDate", "startTime", "endTime")));
    }

    public String normalizeType(String value) {
        String type = StringUtils.hasText(value) ? value.trim().toUpperCase() : "OTHER";
        requireDefinition(type);
        return type;
    }

    public List<String> types() {
        return TYPE_ORDER;
    }

    public List<String> focusFields(String type) {
        return keys(requireDefinition(normalizeType(type)).focusFields());
    }

    public List<String> commonFields(String type) {
        return keys(requireDefinition(normalizeType(type)).commonFields());
    }

    public List<String> allowedDecisionTypes(String type) {
        return List.copyOf(requireDefinition(normalizeType(type)).decisionScopes().keySet());
    }

    public List<String> resolveDecisionScope(
            String type,
            String decisionType,
            List<String> requestedScope
    ) {
        TypeDefinition definition = requireDefinition(normalizeType(type));
        String normalizedDecision = normalizeDecision(decisionType);
        List<String> maximum = definition.decisionScopes().get(normalizedDecision);
        if (maximum == null) {
            throw param(definition.label() + "行程不支持" + decisionLabel(normalizedDecision) + "决策");
        }
        if (requestedScope == null || requestedScope.isEmpty()) {
            return maximum;
        }
        LinkedHashSet<String> requested = new LinkedHashSet<>();
        for (String field : requestedScope) {
            if (!StringUtils.hasText(field) || !fieldLabels.containsKey(field)) {
                throw param("不支持修改行程字段：" + field);
            }
            if (!maximum.contains(field)) {
                throw param(definition.label() + "行程的" + decisionLabel(normalizedDecision)
                        + "决策不能修改" + fieldLabel(field));
            }
            requested.add(field);
        }
        if (requested.isEmpty()) {
            throw param("关联行程投票必须包含至少一个允许修改的字段");
        }
        return List.copyOf(requested);
    }

    public void validateRequestedFields(String type, Map<String, String> fields) {
        Set<String> allowed = allowedFields(normalizeType(type));
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            if (StringUtils.hasText(entry.getValue()) && !allowed.contains(entry.getKey())) {
                throw param(typeLabel(type) + "行程不能填写" + fieldLabel(entry.getKey()));
            }
        }
    }

    public void validatePersistedFields(ActivityItineraryEntity itinerary) {
        validateRequestedFields(itinerary.getItineraryType(), Map.ofEntries(
                Map.entry("transportMode", value(itinerary.getTransportMode())),
                Map.entry("departureName", value(itinerary.getDepartureName())),
                Map.entry("destinationName", value(itinerary.getDestinationName())),
                Map.entry("mealType", value(itinerary.getMealType())),
                Map.entry("restaurantName", value(itinerary.getRestaurantName())),
                Map.entry("activityContent", value(itinerary.getActivityContent())),
                Map.entry("locationName", value(itinerary.getLocationName())),
                Map.entry("address", value(itinerary.getAddress()))));
        validateTimes(
                itinerary.getItineraryType(),
                itinerary.getStartTime(),
                itinerary.getEndTime(),
                itinerary.getAllDay());
    }

    public void clearTypeSpecificFields(ActivityItineraryEntity itinerary) {
        itinerary.setTransportMode(null);
        itinerary.setDepartureName(null);
        itinerary.setDestinationName(null);
        itinerary.setRouteDetail(null);
        itinerary.setMealType(null);
        itinerary.setRestaurantName(null);
        itinerary.setActivityContent(null);
        itinerary.setLocationName(null);
        itinerary.setAddress(null);
    }

    public void clearDisallowedFields(ActivityItineraryEntity itinerary, String type) {
        Set<String> allowed = allowedFields(normalizeType(type));
        if (!allowed.contains("transportMode")) itinerary.setTransportMode(null);
        if (!allowed.contains("departureName")) itinerary.setDepartureName(null);
        if (!allowed.contains("destinationName")) itinerary.setDestinationName(null);
        if (!allowed.contains("mealType")) itinerary.setMealType(null);
        if (!allowed.contains("restaurantName")) itinerary.setRestaurantName(null);
        if (!allowed.contains("activityContent")) itinerary.setActivityContent(null);
        if (!allowed.contains("locationName")) itinerary.setLocationName(null);
        if (!allowed.contains("address")) itinerary.setAddress(null);
        itinerary.setRouteDetail(null);
    }

    public String mergeLegacyRouteDetail(String description, String routeDetail) {
        String normalizedDescription = trim(description);
        String normalizedRoute = trim(routeDetail);
        if (!StringUtils.hasText(normalizedRoute)) return normalizedDescription;
        if (!StringUtils.hasText(normalizedDescription)) return normalizedRoute;
        if (normalizedDescription.contains(normalizedRoute)) return normalizedDescription;
        String merged = normalizedDescription + "\n" + normalizedRoute;
        if (merged.length() > 2000) {
            throw param("备注说明合并历史路线后长度不能超过 2000");
        }
        return merged;
    }

    public String effectiveDescription(ActivityItineraryEntity itinerary) {
        return StringUtils.hasText(itinerary.getDescription())
                ? itinerary.getDescription()
                : trim(itinerary.getRouteDetail());
    }

    public void validateTimes(String type, LocalTime start, LocalTime end, Integer allDay) {
        if (Integer.valueOf(1).equals(allDay) || start == null || end == null) return;
        if ("LODGING".equals(normalizeType(type))) {
            return;
        }
        if (!end.isAfter(start)) {
            throw param("结束时间必须晚于开始时间");
        }
    }

    public String displaySummary(ActivityItineraryEntity itinerary) {
        if ("CANCELED".equals(itinerary.getPlanningStatus())) return "该行程已取消";
        if ("PENDING_DECISION".equals(itinerary.getPlanningStatus())) return "具体方案待决定";
        return switch (normalizeType(itinerary.getItineraryType())) {
            case "TRANSPORT" -> summaryOr(
                    "交通安排",
                    itinerary.getTransportMode(),
                    route(itinerary.getDepartureName(), itinerary.getDestinationName()));
            case "MEAL" -> summaryOr(
                    "用餐安排", itinerary.getMealType(), itinerary.getRestaurantName());
            case "LODGING" -> summaryOr(
                    "住宿安排", itinerary.getLocationName(), itinerary.getAddress());
            case "SIGHTSEEING" -> summaryOr(
                    "景点安排", itinerary.getActivityContent(), itinerary.getLocationName());
            case "ACTIVITY" -> summaryOr(
                    "活动安排", itinerary.getActivityContent(), itinerary.getLocationName());
            default -> summaryOr("其他安排", itinerary.getLocationName());
        };
    }

    public List<ItineraryTypeMetadataResponse> metadata() {
        return definitions.values().stream().map(definition -> new ItineraryTypeMetadataResponse(
                definition.type(),
                definition.label(),
                definition.focusFields(),
                definition.commonFields(),
                List.copyOf(definition.decisionScopes().keySet())))
                .toList();
    }

    public String fieldLabel(String field) {
        return fieldLabels.getOrDefault(field, field);
    }

    public List<String> allSnapshotFields() {
        return List.copyOf(fieldLabels.keySet());
    }

    public boolean isLegacySensitiveScope(List<String> scope) {
        return scope.contains("title") || scope.contains("routeDetail");
    }

    public List<String> legacyDefaultScope(String decisionType) {
        return switch (normalizeDecision(decisionType)) {
            case "TRANSPORT" -> List.of("transportMode");
            case "ROUTE" -> List.of("departureName", "destinationName", "routeDetail");
            case "TIME" -> List.of("itineraryDate", "startTime", "endTime");
            case "RESTAURANT" -> List.of("mealType", "restaurantName", "address");
            case "PLACE" -> List.of("locationName");
            case "CONTENT" -> List.of("activityContent");
            case "ITINERARY_NAME", "OTHER" -> List.of("title");
            default -> List.of();
        };
    }

    private Set<String> allowedFields(String type) {
        TypeDefinition definition = requireDefinition(type);
        LinkedHashSet<String> allowed = new LinkedHashSet<>(keys(definition.commonFields()));
        allowed.addAll(keys(definition.focusFields()));
        return Set.copyOf(allowed);
    }

    private List<String> keys(List<ItineraryFieldMetadata> fields) {
        return fields.stream().map(ItineraryFieldMetadata::key).toList();
    }

    private List<ItineraryFieldMetadata> fields(String... values) {
        if (values.length % 2 != 0) {
            throw new IllegalArgumentException("字段元数据必须按 key、label 成对配置");
        }
        java.util.ArrayList<ItineraryFieldMetadata> result = new java.util.ArrayList<>();
        for (int index = 0; index < values.length; index += 2) {
            result.add(new ItineraryFieldMetadata(values[index], values[index + 1]));
        }
        return List.copyOf(result);
    }

    private void add(
            String type,
            String label,
            List<ItineraryFieldMetadata> focusFields,
            List<ItineraryFieldMetadata> commonFields,
            LinkedHashMap<String, List<String>> decisionScopes
    ) {
        definitions.put(type, new TypeDefinition(
                type,
                label,
                List.copyOf(focusFields),
                List.copyOf(commonFields),
                immutableDecisionScopes(decisionScopes)));
    }

    private LinkedHashMap<String, List<String>> decisions(Object... values) {
        LinkedHashMap<String, List<String>> result = new LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2) {
            @SuppressWarnings("unchecked")
            List<String> fields = (List<String>) values[index + 1];
            result.put((String) values[index], List.copyOf(fields));
        }
        return result;
    }

    private LinkedHashMap<String, List<String>> immutableDecisionScopes(
            LinkedHashMap<String, List<String>> source
    ) {
        LinkedHashMap<String, List<String>> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(key, List.copyOf(value)));
        return result;
    }

    private TypeDefinition requireDefinition(String type) {
        TypeDefinition definition = definitions.get(type);
        if (definition == null) throw param("不支持的行程类型");
        return definition;
    }

    private String typeLabel(String type) {
        return requireDefinition(normalizeType(type)).label();
    }

    private String normalizeDecision(String decisionType) {
        if (!StringUtils.hasText(decisionType)) throw param("决策类型不能为空");
        return decisionType.trim().toUpperCase();
    }

    private String decisionLabel(String decisionType) {
        return switch (decisionType) {
            case "TRANSPORT" -> "交通方式";
            case "ROUTE" -> "出发地和目的地";
            case "TIME" -> "时间";
            case "RESTAURANT" -> "餐厅";
            case "PLACE" -> "地点";
            case "CONTENT" -> "活动内容";
            case "ITINERARY_NAME" -> "行程名称";
            default -> "其他";
        };
    }

    private String summaryOr(String fallback, String... values) {
        String summary = Arrays.stream(values)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .collect(Collectors.joining(" · "));
        return StringUtils.hasText(summary) ? summary : fallback;
    }

    private String route(String departure, String destination) {
        if (StringUtils.hasText(departure) && StringUtils.hasText(destination)) {
            return departure.trim() + " → " + destination.trim();
        }
        return StringUtils.hasText(departure) ? departure.trim() : trim(destination);
    }

    private String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String value(String value) {
        return value == null ? "" : value;
    }

    private BusinessException param(String message) {
        return new BusinessException(ErrorCode.PARAM_ERROR.code(), message);
    }

    private record TypeDefinition(
            String type,
            String label,
            List<ItineraryFieldMetadata> focusFields,
            List<ItineraryFieldMetadata> commonFields,
            LinkedHashMap<String, List<String>> decisionScopes
    ) {
    }
}
