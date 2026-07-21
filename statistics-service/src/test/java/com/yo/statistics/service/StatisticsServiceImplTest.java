package com.yo.statistics.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.yo.statistics.domain.po.StatisticsPO.Accuracy;
import com.yo.statistics.domain.po.StatisticsPO.Confusion;
import com.yo.statistics.mapper.StatisticsMappers.AccuracyMapper;
import com.yo.statistics.mapper.StatisticsMappers.ConfusionMapper;
import com.yo.statistics.mapper.StatisticsMappers.DailyAccuracyMapper;
import com.yo.statistics.mapper.StatisticsMappers.EventMapper;
import com.yo.statistics.mapper.StatisticsMappers.OutcomeFactMapper;
import com.yo.statistics.mapper.StatisticsRebuildMapper;
import com.yo.statistics.service.impl.StatisticsServiceImpl;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.transaction.PlatformTransactionManager;

class StatisticsServiceImplTest {
  @Test
  void dashboardReturnsOnlySystemPrimaryAndAiSources() {
    var accuracyMapper = mock(AccuracyMapper.class);
    var confusionMapper = mock(ConfusionMapper.class);
    var eventMapper = mock(EventMapper.class);
    var redis = mock(StatisticsRedisProjection.class);
    when(redis.get()).thenReturn(Optional.empty());
    when(accuracyMapper.selectList(any())).thenReturn(List.of(
        accuracy("SYSTEM"), accuracy("PRIMARY"), accuracy("AI"), accuracy("SECONDARY")));
    when(confusionMapper.selectList(any())).thenReturn(List.of(
        confusion("SYSTEM"), confusion("TRUTH")));
    when(eventMapper.selectCount(any())).thenReturn(1L);
    var service = new StatisticsServiceImpl(
        accuracyMapper,
        confusionMapper,
        eventMapper,
        mock(DailyAccuracyMapper.class),
        mock(RabbitTemplate.class),
        redis,
        mock(OutcomeFactMapper.class),
        mock(StatisticsRebuildMapper.class),
        mock(RedissonClient.class),
        mock(PlatformTransactionManager.class));

    var result = service.dashboard();

    assertThat(result.accuracy()).extracting(x -> x.sourceType())
        .containsExactly("SYSTEM", "PRIMARY", "AI");
    assertThat(result.confusion()).extracting(x -> x.sourceType())
        .containsExactly("SYSTEM");
  }

  private Accuracy accuracy(String source) {
    var value = new Accuracy();
    value.sourceType = source;
    value.correctCount = 1;
    value.totalCount = 1;
    return value;
  }

  private Confusion confusion(String source) {
    var value = new Confusion();
    value.sourceType = source;
    value.targetCode = "RSV";
    return value;
  }
}
