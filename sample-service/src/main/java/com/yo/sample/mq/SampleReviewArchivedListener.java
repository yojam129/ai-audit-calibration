package com.yo.sample.mq;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yo.sample.domain.po.Sample;
import com.yo.sample.mapper.SampleMapper;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class SampleReviewArchivedListener {
  private final SampleMapper samples;

  public SampleReviewArchivedListener(SampleMapper samples) {
    this.samples = samples;
  }

  @RabbitListener(queues = "${app.queue.sample-review-archived:sample.review.archived.v1}")
  @Transactional
  public void receive(SampleReviewArchivedEvent event) {
    if (!event.isArchived() || event.getSampleId() == null) return;
    samples.update(
        null,
        Wrappers.<Sample>lambdaUpdate()
            .set(Sample::getStatus, "ARCHIVED")
            .eq(Sample::getBusinessId, event.getSampleId().toString())
            .ne(Sample::getStatus, "ARCHIVED"));
  }
}
