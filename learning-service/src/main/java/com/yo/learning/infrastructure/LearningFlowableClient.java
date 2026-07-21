package com.yo.learning.infrastructure;

import com.yo.learning.domain.po.LearningAssignment;
import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestTemplate;

@Component
public class LearningFlowableClient {
  public static final String PROCESS_KEY = "reviewerQualification";
  public static final String TRAINING_TASK = "trainingTask";
  public static final String EXAM_TASK = "examTask";

  private final RestTemplate rest;
  private final String baseUrl;
  private final String callbackBaseUrl;
  private final int processVersion;

  public LearningFlowableClient(
      RestTemplateBuilder builder,
      @Value("${flowable.rest.base-url:http://192.168.1.4:8090/flowable-ui/process-api}") String baseUrl,
      @Value("${flowable.rest.username:admin}") String username,
      @Value("${flowable.rest.password:test}") String password,
      @Value("${flowable.learning.process-version:1}") int processVersion,
      @Value("${learning.workflow.callback-base-url}") String callbackBaseUrl) {
    this.rest =
        builder
            .setConnectTimeout(Duration.ofSeconds(5))
            .setReadTimeout(Duration.ofSeconds(15))
            .basicAuthentication(username, password)
            .build();
    this.baseUrl = trimSlash(baseUrl);
    this.callbackBaseUrl = trimSlash(callbackBaseUrl);
    this.processVersion = processVersion;
  }

  @SuppressWarnings("unchecked")
  public String start(LearningAssignment assignment) {
    if (callbackBaseUrl.isBlank())
      throw new IllegalStateException(
          "LEARNING_WORKFLOW_CALLBACK_URL must be reachable from the Flowable host");
    ensureProcessDeployed();
    String existing = findProcessInstance(String.valueOf(assignment.id));
    if (existing != null) return existing;
    String callbackPrefix =
        callbackBaseUrl
            + "/api/v1/learning/workflow/callback/"
            + assignment.workflowToken
            + "/"
            + assignment.id;
    Map<String, Object> variables = new HashMap<>();
    variables.put("assignmentId", assignment.id);
    variables.put("reviewerId", assignment.reviewerId);
    variables.put("authUserId", assignment.authUserId);
    variables.put("courseCode", assignment.courseCode);
    variables.put("errorType", assignment.errorType);
    variables.put("examRequiredCallbackUrl", callbackPrefix + "/exam-required");
    variables.put("restorePendingCallbackUrl", callbackPrefix + "/restore-pending");
    variables.put("restoreCallbackUrl", callbackPrefix + "/restore");
    Map<String, Object> body =
        Map.of(
            "processDefinitionKey", PROCESS_KEY,
            "businessKey", String.valueOf(assignment.id),
            "name", "Reviewer qualification - " + assignment.reviewerId,
            "variables", toRestVariables(variables));
    Map<String, Object> response =
        Objects.requireNonNull(
            rest.postForEntity(baseUrl + "/runtime/process-instances", entity(body), Map.class)
                .getBody());
    return Objects.toString(response.get("id"));
  }

  @SuppressWarnings("unchecked")
  private String findProcessInstance(String businessKey) {
    Map<String, Object> response =
        Objects.requireNonNull(
            rest.getForEntity(
                    baseUrl
                        + "/runtime/process-instances?processDefinitionKey="
                        + PROCESS_KEY
                        + "&businessKey="
                        + businessKey,
                    Map.class)
                .getBody());
    List<Map<String, Object>> instances = (List<Map<String, Object>>) response.get("data");
    if (instances == null || instances.isEmpty()) return null;
    return Objects.toString(instances.get(0).get("id"));
  }

  public void completeCurrentTask(
      String processInstanceId, String expectedTaskKey, Map<String, Object> variables) {
    TaskInfo task = queryCurrentTask(processInstanceId);
    if (task == null || !expectedTaskKey.equals(task.taskDefinitionKey())) {
      throw new IllegalStateException("Flowable task is not " + expectedTaskKey);
    }
    Map<String, Object> body =
        Map.of("action", "complete", "variables", toRestVariables(variables));
    rest.exchange(
        baseUrl + "/runtime/tasks/" + task.id(),
        HttpMethod.POST,
        entity(body),
        Void.class);
  }

  @SuppressWarnings("unchecked")
  public TaskInfo queryCurrentTask(String processInstanceId) {
    Map<String, Object> response =
        Objects.requireNonNull(
            rest.getForEntity(
                    baseUrl + "/runtime/tasks?processInstanceId=" + processInstanceId, Map.class)
                .getBody());
    List<Map<String, Object>> tasks = (List<Map<String, Object>>) response.get("data");
    if (tasks == null || tasks.isEmpty()) return null;
    Map<String, Object> task = tasks.get(0);
    return new TaskInfo(Objects.toString(task.get("id")), Objects.toString(task.get("taskDefinitionKey")));
  }

  @SuppressWarnings("unchecked")
  public synchronized void ensureProcessDeployed() {
    Map<String, Object> response =
        Objects.requireNonNull(
            rest.getForEntity(
                    baseUrl
                        + "/repository/process-definitions?key="
                        + PROCESS_KEY
                        + "&latest=true",
                    Map.class)
                .getBody());
    List<Map<String, Object>> definitions = (List<Map<String, Object>>) response.get("data");
    if (definitions != null
        && definitions.stream()
            .map(definition -> (Number) definition.getOrDefault("version", 0))
            .anyMatch(version -> version.intValue() >= processVersion)) return;
    try {
      ClassPathResource resource =
          new ClassPathResource("processes/reviewer-qualification.bpmn20.xml");
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      resource.getInputStream().transferTo(bytes);
      ByteArrayResource file =
          new ByteArrayResource(bytes.toByteArray()) {
            @Override
            public String getFilename() {
              return "reviewer-qualification.bpmn20.xml";
            }
          };
      LinkedMultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
      body.add("file", file);
      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.MULTIPART_FORM_DATA);
      rest.postForEntity(
          baseUrl + "/repository/deployments", new HttpEntity<>(body, headers), Map.class);
    } catch (Exception exception) {
      throw new IllegalStateException("failed to deploy Flowable learning process", exception);
    }
  }

  private HttpEntity<Map<String, Object>> entity(Map<String, Object> body) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    return new HttpEntity<>(body, headers);
  }

  private List<Map<String, Object>> toRestVariables(Map<String, Object> variables) {
    return variables.entrySet().stream()
        .filter(entry -> entry.getValue() != null)
        .map(
            entry -> {
              Map<String, Object> variable = new HashMap<>();
              variable.put("name", entry.getKey());
              variable.put("value", entry.getValue());
              return variable;
            })
        .toList();
  }

  private static String trimSlash(String value) {
    return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
  }

  public record TaskInfo(String id, String taskDefinitionKey) {}
}
