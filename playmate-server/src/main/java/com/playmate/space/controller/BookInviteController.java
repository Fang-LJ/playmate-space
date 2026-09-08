package com.playmate.space.controller;

import com.playmate.space.common.ApiResponse;
import com.playmate.space.service.book.BookService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/book-invites")
public class BookInviteController {
    private final BookService service;
    public BookInviteController(BookService service) { this.service=service; }
    @GetMapping("/{code}") public ApiResponse<?> preview(@PathVariable String code) { return ApiResponse.success(service.invite(code)); }
}
