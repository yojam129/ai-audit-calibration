package com.yo.api.annotations;

import java.lang.annotation.*;
import org.springframework.cloud.openfeign.EnableFeignClients;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@EnableFeignClients(basePackages = "com.yo.api.client")
public @interface EnableYoFeignClients {}
