package com.playmate.space.controller;

import com.playmate.space.common.ApiResponse;
import com.playmate.space.common.ErrorCode;
import com.playmate.space.common.exception.BusinessException;
import com.playmate.space.dto.expense.*;
import com.playmate.space.service.SettlementService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/activities/{activityId}/settlements")
public class ActivitySettlementController {
    private final SettlementService settlementService;
    private final boolean settlementTransferEnabled;
    public ActivitySettlementController(SettlementService settlementService,
                                        @Value("${playmate.features.settlement-transfer-enabled:false}") boolean settlementTransferEnabled) {
        this.settlementService = settlementService;
        this.settlementTransferEnabled = settlementTransferEnabled;
    }
    @GetMapping("/summary") public ApiResponse<SettlementSummaryResponse> summary(@PathVariable Long activityId) { return ApiResponse.success(settlementService.summary(activityId)); }
    @GetMapping("/history") public ApiResponse<List<SettlementHistoryResponse>> history(@PathVariable Long activityId) { return ApiResponse.success(settlementService.history(activityId)); }
    @PostMapping("/complete") public ApiResponse<SettlementHistoryResponse> complete(@PathVariable Long activityId, @Valid @RequestBody CompleteSettlementRequest request) { requireTransferEnabled(); return ApiResponse.success(settlementService.complete(activityId, request)); }
    @PostMapping("/{settlementId}/cancel") public ApiResponse<SettlementHistoryResponse> cancel(@PathVariable Long activityId, @PathVariable Long settlementId, @RequestBody(required = false) CancelSettlementRequest request) { requireTransferEnabled(); return ApiResponse.success(settlementService.cancel(activityId, settlementId, request)); }
    private void requireTransferEnabled() {
        if (!settlementTransferEnabled) throw new BusinessException(ErrorCode.BUSINESS_ERROR.code(), "当前版本暂未开放转账状态功能");
    }
}
