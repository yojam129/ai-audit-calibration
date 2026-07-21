package com.yo.sample.mq;

import com.yo.sample.service.SampleService;
import java.util.Map;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class AiInferenceCompletedListener {
  private final SampleService samples;

  public AiInferenceCompletedListener(SampleService samples) {
    this.samples = samples;
  }

  @RabbitListener(queues = "${app.queue.sample-ai-inference:sample.ai-inference.completed.v1}")
  public void receive(Map<String, Object> event) {
    Object runNo = event.get("runNo");
    if (runNo != null) samples.markAiCompleted(runNo.toString());
  }
}
