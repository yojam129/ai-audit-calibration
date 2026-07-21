package com.yo.statistics.mq;

import com.yo.statistics.service.*;
import org.springframework.amqp.rabbit.annotation.*;
import org.springframework.stereotype.*;

@Component
public class TruthMetricListener {
  private final StatisticsService service;

  public TruthMetricListener(StatisticsService s) {
    service = s;
  }

  @RabbitListener(queues = "${app.queue.truth:statistics.truth.v1}")
  public void receive(TruthMetricEvent e) {
    service.consume(e);
  }
}
