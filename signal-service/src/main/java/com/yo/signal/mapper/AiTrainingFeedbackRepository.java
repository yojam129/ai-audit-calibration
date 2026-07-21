package com.yo.signal.mapper;

import com.yo.signal.domain.po.AiTrainingFeedback;
import java.time.Instant;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface AiTrainingFeedbackRepository
    extends MongoRepository<AiTrainingFeedback, String> {
  boolean existsByFeedbackKey(String feedbackKey);

  List<AiTrainingFeedback> findByStatusAndConfirmedAtGreaterThanEqualAndConfirmedAtLessThan(
      String status, Instant from, Instant to);

  List<AiTrainingFeedback> findByTrainingKey(String trainingKey);
}
