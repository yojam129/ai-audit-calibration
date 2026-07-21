package com.yo.scheduler.config;

import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;

@Configuration
public class XxlJobConfiguration {
  @Bean
  public XxlJobSpringExecutor executor(
      @Value("${xxl.job.admin.addresses}") String addresses,
      @Value("${xxl.job.executor.appname}") String appName,
      @Value("${xxl.job.executor.ip:}") String ip,
      @Value("${xxl.job.executor.port:9999}") int port,
      @Value("${xxl.job.accessToken:}") String token) {
    var e = new XxlJobSpringExecutor();
    e.setAdminAddresses(addresses);
    e.setAppname(appName);
    e.setIp(ip);
    e.setPort(port);
    e.setAccessToken(token);
    return e;
  }
}
