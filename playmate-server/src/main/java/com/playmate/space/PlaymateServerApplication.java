package com.playmate.space;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan("com.playmate.space.mapper")
@SpringBootApplication
public class PlaymateServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(PlaymateServerApplication.class, args);
    }
}
