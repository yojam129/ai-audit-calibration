package com.yo.sample.config;

import com.baomidou.mybatisplus.extension.plugins.*;
import com.baomidou.mybatisplus.extension.plugins.inner.*;
import org.springframework.context.annotation.*;

@Configuration
public class MybatisConfig {
  @Bean
  MybatisPlusInterceptor interceptor() {
    var i = new MybatisPlusInterceptor();
    i.addInnerInterceptor(new PaginationInnerInterceptor());
    return i;
  }
}
