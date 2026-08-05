package com.playmate.space.controller;

import com.playmate.space.common.ApiResponse;
import com.playmate.space.dto.expense.*;
import com.playmate.space.service.ExpenseService;
import com.playmate.space.service.SettlementService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/activities/{activityId}/expenses")
public class ActivityExpenseController {
    private final ExpenseService expenseService;
    private final SettlementService settlementService;
    public ActivityExpenseController(ExpenseService expenseService, SettlementService settlementService) { this.expenseService = expenseService; this.settlementService = settlementService; }
    @GetMapping("/summary") public ApiResponse<ExpenseSummaryResponse> summary(@PathVariable Long activityId) { return ApiResponse.success(settlementService.expenseSummary(activityId)); }
    @GetMapping public ApiResponse<List<ExpenseListItemResponse>> list(@PathVariable Long activityId, @RequestParam(required = false) String category,
                                                                         @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer pageSize,
                                                                         @RequestParam(required = false) String sort) { return ApiResponse.success(expenseService.list(activityId, category, page, pageSize, sort)); }
    @PostMapping public ApiResponse<ExpenseDetailResponse> create(@PathVariable Long activityId, @Valid @RequestBody SaveExpenseRequest request) { return ApiResponse.success(expenseService.create(activityId, request)); }
    @GetMapping("/members") public ApiResponse<List<ExpenseMemberResponse>> members(@PathVariable Long activityId) { return ApiResponse.success(settlementService.members(activityId)); }
    @GetMapping("/{expenseId}") public ApiResponse<ExpenseDetailResponse> detail(@PathVariable Long activityId, @PathVariable Long expenseId) { return ApiResponse.success(expenseService.detail(activityId, expenseId)); }
    @PutMapping("/{expenseId}") public ApiResponse<ExpenseDetailResponse> update(@PathVariable Long activityId, @PathVariable Long expenseId, @Valid @RequestBody SaveExpenseRequest request) { return ApiResponse.success(expenseService.update(activityId, expenseId, request)); }
    @PostMapping("/{expenseId}/void") public ApiResponse<ExpenseDetailResponse> voidExpense(@PathVariable Long activityId, @PathVariable Long expenseId, @RequestBody(required = false) VoidExpenseRequest request) { return ApiResponse.success(expenseService.voidExpense(activityId, expenseId, request)); }
}
