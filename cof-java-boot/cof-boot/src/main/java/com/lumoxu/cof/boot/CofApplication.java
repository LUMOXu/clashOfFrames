package com.lumoxu.cof.boot;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.lumoxu.cof")
@MapperScan("com.lumoxu.cof.domain.mapper")
@EnableScheduling
public class CofApplication {

    public static void main(String[] args) {
        SpringApplication.run(CofApplication.class, args);
    }
}
