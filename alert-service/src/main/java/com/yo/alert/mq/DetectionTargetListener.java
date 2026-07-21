package com.yo.alert.mq;

import com.yo.alert.service.PositiveRateService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class DetectionTargetListener {
  private final PositiveRateService service;

  public DetectionTargetListener(PositiveRateService service) {
    this.service = service;
  }

  @RabbitListener(queues = "${app.queue.detection-target:alert.detection-target.v1}")
  public void receive(DetectionTargetCompletedEvent event) {
    service.consume(event);
  }
}
