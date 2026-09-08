package com.playmate.space.controller;

import com.playmate.space.common.ApiResponse;
import com.playmate.space.dto.book.BookRequests;
import com.playmate.space.service.book.BookService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/books")
public class BookController {
    private final BookService service;
    public BookController(BookService service) { this.service=service; }
    @GetMapping public ApiResponse<?> list(@RequestParam(defaultValue="OPEN") String status,@RequestParam(defaultValue="1") int page) { return ApiResponse.success(service.list(status,page)); }
    @PostMapping public ApiResponse<?> create(@Valid @RequestBody BookRequests.Create request) { return ApiResponse.success(service.create(request)); }
    @GetMapping("/join-status") public ApiResponse<?> joinStatus(@RequestParam String code) { return ApiResponse.success(service.joinStatus(code)); }
    @PostMapping("/join") public ApiResponse<?> join(@Valid @RequestBody BookRequests.Join request) { return ApiResponse.success(service.join(request)); }
    @GetMapping("/{id}") public ApiResponse<?> dashboard(@PathVariable long id) { return ApiResponse.success(service.dashboard(id)); }
    @PutMapping("/{id}") public ApiResponse<?> rename(@PathVariable long id,@Valid @RequestBody BookRequests.Rename request) { return ApiResponse.success(service.rename(id,request)); }
    @PostMapping("/{id}/state") public ApiResponse<?> state(@PathVariable long id,@Valid @RequestBody BookRequests.State request) { return ApiResponse.success(service.state(id,request)); }
    @PostMapping("/{id}/members") public ApiResponse<?> member(@PathVariable long id,@Valid @RequestBody BookRequests.Member request) { return ApiResponse.success(service.addMember(id,request)); }
    @PutMapping("/{id}/members/{memberId}") public ApiResponse<?> renameMember(@PathVariable long id,@PathVariable long memberId,@Valid @RequestBody BookRequests.Member request) { return ApiResponse.success(service.renameMember(id,memberId,request)); }
    @PostMapping("/{id}/members/{memberId}/state") public ApiResponse<?> memberState(@PathVariable long id,@PathVariable long memberId,@Valid @RequestBody BookRequests.MemberState request) { return ApiResponse.success(service.memberState(id,memberId,request)); }
    @PostMapping("/{id}/claims/{claimId}") public ApiResponse<?> decide(@PathVariable long id,@PathVariable long claimId,@Valid @RequestBody BookRequests.Decision request) { service.decide(id,claimId,request); return ApiResponse.success(null); }
    @GetMapping("/{id}/expenses") public ApiResponse<?> expenses(@PathVariable long id,@RequestParam(required=false) String category,@RequestParam(defaultValue="1") int page) { return ApiResponse.success(service.expenses(id,category,page)); }
    @GetMapping("/{id}/expenses/{expenseId}") public ApiResponse<?> expense(@PathVariable long id,@PathVariable long expenseId) { return ApiResponse.success(service.expense(id,expenseId)); }
    @PostMapping("/{id}/expenses") public ApiResponse<?> save(@PathVariable long id,@Valid @RequestBody BookRequests.Expense request) { return ApiResponse.success(service.save(id,null,request)); }
    @PutMapping("/{id}/expenses/{expenseId}") public ApiResponse<?> update(@PathVariable long id,@PathVariable long expenseId,@Valid @RequestBody BookRequests.Expense request) { return ApiResponse.success(service.save(id,expenseId,request)); }
    @PostMapping("/{id}/expenses/{expenseId}/void") public ApiResponse<?> voidExpense(@PathVariable long id,@PathVariable long expenseId,@Valid @RequestBody BookRequests.VoidExpense request) { service.voidExpense(id,expenseId,request); return ApiResponse.success(null); }
}
