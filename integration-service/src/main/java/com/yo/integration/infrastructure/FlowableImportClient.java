package com.yo.integration.infrastructure;

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
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

@Component
public class FlowableImportClient {
  private static final String PROCESS_KEY = "importRecovery";

  private final RestTemplate rest;
  private final String baseUrl;
  private final String callbackBaseUrl;
  private final int requiredVersion;

  public FlowableImportClient(
      RestTemplateBuilder builder,
      @Value("${flowable.rest.base-url}") String baseUrl,
      @Value("${flowable.rest.username}") String username,
      @Value("${flowable.rest.password}") String password,
      @Value("${flowable.rest.import-process-version:2}") int requiredVersion,
      @Value("${flowable.import.callback-base-url}") String callbackBaseUrl) {
    this.rest = builder.setConnectTimeout(Duration.ofSeconds(5))
        .setReadTimeout(Duration.ofSeconds(10)).basicAuthentication(username, password).build();
    this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    this.callbackBaseUrl = trimSlash(callbackBaseUrl);
    this.requiredVersion = Math.max(2, requiredVersion);
  }

  @SuppressWarnings("unchecked")
  public String start(String businessKey, String name, Map<String, Object> variables) {
    if (callbackBaseUrl.isBlank()) {
      throw new IllegalStateException(
          "INTEGRATION_WORKFLOW_CALLBACK_URL must be reachable from the Flowable host");
    }
    ensureDeployed();
    String existing = findProcessInstance(businessKey);
    if (existing != null) return existing;
    Map<String, Object> body = new HashMap<>();
    body.put("processDefinitionKey", PROCESS_KEY);
    body.put("businessKey", businessKey);
    body.put("name", name);
    body.put("variables", variables(variables));
    Map<String, Object> response = rest.postForObject(
        baseUrl + "/runtime/process-instances", entity(body), Map.class);
    return Objects.toString(Objects.requireNonNull(response).get("id"));
  }

  @SuppressWarnings("unchecked")
  public TaskInfo task(String processInstanceId) {
    Map<String, Object> response = rest.getForObject(
        baseUrl + "/runtime/tasks?processInstanceId=" + processInstanceId, Map.class);
    List<Map<String, Object>> data = (List<Map<String, Object>>) Objects.requireNonNull(response).get("data");
    if (data == null || data.isEmpty()) return null;
    Map<String, Object> task = data.get(0);
    return new TaskInfo(
        Objects.toString(task.get("id")),
        Objects.toString(task.get("taskDefinitionKey")),
        Objects.toString(task.get("assignee"), null));
  }

  public void claim(String taskId, String userId) {
    rest.exchange(baseUrl + "/runtime/tasks/" + taskId, HttpMethod.POST,
        entity(Map.of("action", "claim", "assignee", userId)), Void.class);
  }

  public void complete(String taskId, String resolution, String reason) {
    rest.exchange(baseUrl + "/runtime/tasks/" + taskId, HttpMethod.POST,
        entity(Map.of("action", "complete", "variables", variables(Map.of(
            "resolution", resolution, "reason", reason == null ? "" : reason)))), Void.class);
  }

  public void cancel(String processInstanceId, String reason) {
    String url =
        UriComponentsBuilder.fromHttpUrl(
                baseUrl + "/runtime/process-instances/" + processInstanceId)
            .queryParam("deleteReason", reason)
            .encode()
            .toUriString();
    try {
      rest.exchange(url, HttpMethod.DELETE, HttpEntity.EMPTY, Void.class);
    } catch (HttpClientErrorException.NotFound alreadyEnded) {
      // An already-ended legacy instance is safe to replace.
    }
  }

  public String callbackUrl(
      String workflowToken, String failureScope, long subjectId, String resolution) {
    return callbackBaseUrl
        + "/api/v1/integration/workflow/callback/import-recovery/"
        + workflowToken
        + "/"
        + failureScope
        + "/"
        + subjectId
        + "/"
        + resolution;
  }

  @SuppressWarnings("unchecked")
  private String findProcessInstance(String businessKey) {
    String url =
        UriComponentsBuilder.fromHttpUrl(baseUrl + "/runtime/process-instances")
            .queryParam("processDefinitionKey", PROCESS_KEY)
            .queryParam("businessKey", businessKey)
            .encode()
            .toUriString();
    Map<String, Object> response = rest.getForObject(url, Map.class);
    List<Map<String, Object>> data =
        (List<Map<String, Object>>) Objects.requireNonNull(response).get("data");
    if (data == null || data.isEmpty()) return null;
    return Objects.toString(data.get(0).get("id"));
  }

  @SuppressWarnings("unchecked")
  private synchronized void ensureDeployed() {
    Map<String, Object> response = rest.getForObject(
        baseUrl + "/repository/process-definitions?key=" + PROCESS_KEY + "&latest=true", Map.class);
    List<Map<String, Object>> definitions =
        (List<Map<String, Object>>) Objects.requireNonNull(response).get("data");
    if (definitions != null && definitions.stream().anyMatch(definition ->
        ((Number) definition.getOrDefault("version", 0)).intValue() >= requiredVersion)) return;
    try {
      ClassPathResource resource = new ClassPathResource("processes/import-recovery.bpmn20.xml");
      ByteArrayResource file = new ByteArrayResource(resource.getInputStream().readAllBytes()) {
        @Override public String getFilename() { return "import-recovery.bpmn20.xml"; }
      };
      LinkedMultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
      body.add("file", file);
      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.MULTIPART_FORM_DATA);
      rest.postForEntity(baseUrl + "/repository/deployments", new HttpEntity<>(body, headers), Map.class);
    } catch (Exception exception) {
      throw new IllegalStateException("failed to deploy import recovery process", exception);
    }
  }

  private HttpEntity<Map<String, Object>> entity(Map<String, Object> body) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    return new HttpEntity<>(body, headers);
  }

  private List<Map<String, Object>> variables(Map<String, Object> values) {
    return values.entrySet().stream()
        .filter(entry -> entry.getValue() != null)
        .map(entry -> Map.<String, Object>of(
            "name", entry.getKey(), "value", entry.getValue()))
        .toList();
  }

  private static String trimSlash(String value) {
    return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
  }

  public record TaskInfo(String id, String taskDefinitionKey, String assignee) {}
}
