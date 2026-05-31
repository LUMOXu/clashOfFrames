package com.lumoxu.cof.api.controller;

import com.lumoxu.cof.api.auth.AuthInterceptor;
import com.lumoxu.cof.api.config.ApiWebConfig;
import com.lumoxu.cof.api.config.GlobalExceptionHandler;
import com.lumoxu.cof.api.ApiTestApplication;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ContextConfiguration(classes = ApiTestApplication.class)
@AutoConfigureMockMvc
@Import({AuthInterceptor.class, ApiWebConfig.class, GlobalExceptionHandler.class})
public @interface MvcTestConfig {
}
