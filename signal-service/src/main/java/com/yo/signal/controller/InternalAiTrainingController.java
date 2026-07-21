package com.yo.signal.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yo.api.internal.InternalHmac;
import com.yo.api.internal.InternalOperations;
import com.yo.signal.service.AiFeedbackTrainingService;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InternalAiTrainingController {
  private final AiFeedbackTrainingService training;
  private final ObjectMapper json;
  private final String secret;

  public InternalAiTrainingController(
      AiFeedbackTrainingService training,
      ObjectMapper json,
      @Value("${internal.hmac.secret:}") String secret) {
    this.training = training;
    this.json = json;
    this.secret = secret;
  }

  @PostMapping(InternalOperations.TRAIN_AI_FEEDBACK)
  public AiFeedbackTrainingService.TrainingResult train(
      @RequestBody(required = false) String body,
      @RequestHeader("X-Internal-Timestamp") String timestamp,
      @RequestHeader("X-Internal-Signature") String signature) throws Exception {
    String payload = body == null ? "" : body;
    InternalHmac.verify(
        "POST",
        InternalOperations.TRAIN_AI_FEEDBACK,
        timestamp,
        signature,
        payload.getBytes(StandardCharsets.UTF_8),
        secret);
    var window =
        payload.isBlank()
            ? new AiFeedbackTrainingService.TrainingWindow()
            : json.readValue(payload, AiFeedbackTrainingService.TrainingWindow.class);
    return training.train(window);
  }
}
