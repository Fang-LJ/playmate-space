package com.playmate.space.controller;

import com.playmate.space.common.ApiResponse;
import com.playmate.space.dto.itinerary.ItineraryTypeMetadataResponse;
import com.playmate.space.service.ItineraryTypePolicy;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/itineraries")
public class ItineraryMetadataController {
    private final ItineraryTypePolicy typePolicy;

    public ItineraryMetadataController(ItineraryTypePolicy typePolicy) {
        this.typePolicy = typePolicy;
    }

    @GetMapping("/type-metadata")
    public ApiResponse<List<ItineraryTypeMetadataResponse>> typeMetadata() {
        return ApiResponse.success(typePolicy.metadata());
    }
}
