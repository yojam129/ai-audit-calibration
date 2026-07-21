package com.yo.reviewworkflow.infrastructure;

import java.time.Duration;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class FlowableRestClient {
  private final RestTemplate rest;
  private final String baseUrl;
  private final int processVersion;

  public FlowableRestClient(
      RestTemplateBuilder builder,
      @Value("${flowable.rest.base-url:http://192.168.1.4:8090/flowable-ui/process-api}") String baseUrl,
      @Value("${flowable.rest.username:admin}") String username,
      @Value("${flowable.rest.password:test}") String password,
      @Value("${flowable.rest.process-version:4}") int processVersion) {
    this.rest = builder.setConnectTimeout(Duration.ofSeconds(5))
                       .setReadTimeout(Duration.ofSeconds(10))
                       .basicAuthentication(username, password)
                       .build();
    this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    this.processVersion = processVersion;
  }

  @SuppressWarnings("unchecked")
  public String startProcess(String processKey, Map<String, Object> variables) {
    ensureProcessDeployed(processKey);
    String businessKey = Objects.toString(variables.get("sampleId"), "");
    String existing = findRuntimeProcess(businessKey);
    if (existing != null) return existing;
    var body = Map.of(
        "processDefinitionKey", processKey,
        "businessKey", businessKey,
        "name", "Sample audit - " + businessKey,
        "variables", toRestVars(variables));
    var rsp = rest.postForEntity(baseUrl + "/runtime/process-instances", entity(body), Map.class);
    return (String) rsp.getBody().get("id");
  }

  @SuppressWarnings("unchecked")
  public synchronized void ensureProcessDeployed(String processKey) {
    var response = rest.getForEntity(
        baseUrl + "/repository/process-definitions?key=" + processKey + "&latest=true", Map.class);
    var definitions = (List<?>) Objects.requireNonNull(response.getBody()).get("data");
    if (definitions != null && definitions.stream()
        .filter(Map.class::isInstance)
        .map(Map.class::cast)
        .map(definition -> ((Number) definition.getOrDefault("version", 0)).intValue())
        .anyMatch(version -> version >= processVersion)) return;
    try {
      var resource = new ClassPathResource("processes/secondary-review.bpmn20.xml");
      var file = new ByteArrayResource(resource.getInputStream().readAllBytes()) {
        @Override public String getFilename() { return "secondary-review.bpmn20.xml"; }
      };
      var body = new LinkedMultiValueMap<String, Object>();
      body.add("file", file);
      var headers = new HttpHeaders();
      headers.setContentType(MediaType.MULTIPART_FORM_DATA);
      rest.postForEntity(baseUrl + "/repository/deployments", new HttpEntity<>(body, headers), Map.class);
    } catch (Exception exception) {
      throw new IllegalStateException("failed to deploy Flowable process " + processKey, exception);
    }
  }

  @SuppressWarnings("unchecked")
  public String findRuntimeProcess(String businessKey) {
    var response = rest.getForEntity(
        baseUrl + "/runtime/process-instances?processDefinitionKey=sampleAuditMain&businessKey="
            + businessKey,
        Map.class);
    var data = (List<Map<String, Object>>) Objects.requireNonNull(response.getBody()).get("data");
    return data == null || data.isEmpty() ? null : Objects.toString(data.getFirst().get("id"), null);
  }

  @SuppressWarnings("unchecked")
  public ExecutionInfo queryExecution(String processInstanceId, String activityId) {
    var response = rest.getForEntity(
        baseUrl + "/runtime/executions?processInstanceId=" + processInstanceId
            + "&activityId=" + activityId,
        Map.class);
    var data = (List<Map<String, Object>>) Objects.requireNonNull(response.getBody()).get("data");
    if (data == null || data.isEmpty()) return null;
    var execution = data.getFirst();
    return new ExecutionInfo(
        Objects.toString(execution.get("id"), null),
        Objects.toString(execution.get("activityId"), null));
  }

  public boolean signal(String processInstanceId, String activityId, Map<String, Object> variables) {
    ExecutionInfo execution = queryExecution(processInstanceId, activityId);
    if (execution == null) return false;
    var body = Map.of(
        "action", "signal",
        "variables", toRestVars(variables));
    rest.exchange(
        baseUrl + "/runtime/executions/" + execution.id(),
        HttpMethod.PUT,
        entity(body),
        Void.class);
    return true;
  }

  @SuppressWarnings("unchecked")
  public TaskInfo queryTask(String processInstanceId) {
    var rsp = rest.getForEntity(baseUrl + "/runtime/tasks?processInstanceId=" + processInstanceId, Map.class);
    var data = (List) rsp.getBody().get("data");
    if (data == null || data.isEmpty()) return null;
    var t = (Map) data.get(0);
    return new TaskInfo((String) t.get("id"), (String) t.get("taskDefinitionKey"));
  }

  public TaskInfo queryTask(String processInstanceId, String taskDefinitionKey) {
    return queryTasks(processInstanceId).stream()
        .filter(task -> taskDefinitionKey.equals(task.taskDefinitionKey()))
        .findFirst()
        .orElse(null);
  }

  @SuppressWarnings("unchecked")
  public List<TaskInfo> queryTasks(String processInstanceId) {
    var rsp = rest.getForEntity(baseUrl + "/runtime/tasks?processInstanceId=" + processInstanceId, Map.class);
    var data = (List) rsp.getBody().get("data");
    if (data == null) return List.of();
    return data.stream()
        .map(t -> new TaskInfo((String) ((Map) t).get("id"), (String) ((Map) t).get("taskDefinitionKey")))
        .toList();
  }

  public void claimTask(String taskId, String userId) {
    var body = (Map<String,Object>)(Map)Map.of("action", "claim", "assignee", userId);
    rest.exchange(baseUrl + "/runtime/tasks/" + taskId, HttpMethod.POST, entity(body), Void.class);
  }

  public void completeTask(String taskId, Map<String, Object> variables) {
    var body = Map.of("action", "complete", "variables", toRestVars(variables));
    rest.exchange(baseUrl + "/runtime/tasks/" + taskId, HttpMethod.POST, entity(body), Void.class);
  }

  private HttpEntity<Map<String, Object>> entity(Map<String, Object> body) {
    var headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    return new HttpEntity<>(body, headers);
  }

  private List<Map<String, Object>> toRestVars(Map<String, Object> vars) {
    return vars.entrySet().stream()
        .map(e -> {
            Map<String, Object> m = new HashMap<>();
            m.put("name", e.getKey());
            m.put("value", e.getValue());
            return m;
        })
        .toList();
  }

  public record TaskInfo(String id, String taskDefinitionKey) {}
  public record ExecutionInfo(String id, String activityId) {}
}
