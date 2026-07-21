package com.yo.learning.mq;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.yo.learning.domain.dto.LearningDTO;
import com.yo.learning.mapper.LearningMapper;
import com.yo.learning.service.LearningService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class TrainingTriggerListener {
  private final LearningMapper mapper;
  private final LearningService service;

  public TrainingTriggerListener(LearningMapper mapper, LearningService service) {
    this.mapper = mapper;
    this.service = service;
  }

  @RabbitListener(queues = "${app.queue.training-trigger:learning.training-trigger.v1}")
  @Transactional
  public void consume(TrainingTriggerEvent event) {
    if (mapper.selectCount(
            new QueryWrapper<com.yo.learning.domain.po.LearningAssignment>()
                .eq("external_event_id", event.eventId()))
        > 0) return;
    if (event.authUserId() == null)
      throw new IllegalArgumentException("training event must include explicit authUserId");
    long id =
        service.assign(
        new LearningDTO(
            event.reviewerId(),
            event.authUserId(),
            event.courseCode(),
            event.errorType(),
            Math.max(1, event.dueDays())));
    mapper.update(
        null,
        new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<
                com.yo.learning.domain.po.LearningAssignment>()
            .set("external_event_id", event.eventId())
            .set("focus_sample_id", event.sampleId())
            .set("focus_sample_no", event.sampleNo())
            .set("focus_chamber", event.chamber())
            .set("focus_channel_code", event.channelCode())
            .set("focus_target_code", event.targetCode())
            .eq("id", id));
  }
}
