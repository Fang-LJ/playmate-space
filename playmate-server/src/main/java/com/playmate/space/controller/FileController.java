package com.playmate.space.controller;

import com.playmate.space.common.ApiResponse;
import com.playmate.space.service.FileService;
import com.playmate.space.vo.FileUploadResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/files")
public class FileController {

    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    @PostMapping("/upload")
    public ApiResponse<FileUploadResponse> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("fileType") String fileType
    ) {
        return ApiResponse.success(fileService.upload(file, fileType));
    }
}
