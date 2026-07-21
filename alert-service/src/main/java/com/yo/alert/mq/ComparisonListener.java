package com.yo.alert.mq;

import com.yo.alert.service.*;
import org.springframework.amqp.rabbit.annotation.*;
import org.springframework.stereotype.*;

@Component
public class ComparisonListener {
  private final AlertService service;

  public ComparisonListener(AlertService s) {
    service = s;
  }

  @RabbitListener(queues = "${app.queue.comparison:alert.comparison.v1}")
  public void receive(ComparisonEvent e) {
    service.consume(e);
  }
}
