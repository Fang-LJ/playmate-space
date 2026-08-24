package com.playmate.space.service.photo;

import com.playmate.space.entity.FileEntity;
import com.playmate.space.mapper.FileMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PhotoFilePersistenceService {
    private final FileMapper fileMapper;
    public PhotoFilePersistenceService(FileMapper fileMapper) { this.fileMapper = fileMapper; }
    @Transactional
    public void insert(FileEntity entity) { fileMapper.insert(entity); }
}
