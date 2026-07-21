package com.yo.alert.infrastructure;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class FlowableReviewLinkClient {
  private static final String AUDIT_PROCESS_KEY = "sampleAuditMain";
  private static final String SECONDARY_REVIEW_TASK_KEY = "secondaryReview";

  private final RestTemplate rest;
  private final String baseUrl;

  public FlowableReviewLinkClient(
      RestTemplateBuilder builder,
      @Value("${flowable.rest.base-url}") String baseUrl,
      @Value("${flowable.rest.username}") String username,
      @Value("${flowable.rest.password}") String password) {
    this.rest = builder.setConnectTimeout(Duration.ofSeconds(5))
        .setReadTimeout(Duration.ofSeconds(10)).basicAuthentication(username, password).build();
    this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
  }

  public ProcessLink findSecondaryReview(UUID sampleId) {
    ProcessLink active = find(
        "/runtime/process-instances", sampleId, false);
    return active != null ? active : find(
        "/history/historic-process-instances", sampleId, true);
  }

  @SuppressWarnings("unchecked")
  private ProcessLink find(String path, UUID sampleId, boolean historic) {
    UriComponentsBuilder query = UriComponentsBuilder.fromHttpUrl(baseUrl + path)
        .queryParam("processDefinitionKey", AUDIT_PROCESS_KEY)
        .queryParam("businessKey", sampleId)
        .queryParam("size", 1);
    if (historic) query.queryParam("finished", true);
    String uri = query.build().encode().toUriString();
    Map<String, Object> response = rest.getForObject(uri, Map.class);
    List<Map<String, Object>> rows = (List<Map<String, Object>>)
        Objects.requireNonNull(response).get("data");
    if (rows == null || rows.isEmpty()) return null;
    String processId = Objects.toString(rows.get(0).get("id"));
    TaskInfo task = historic ? null : secondaryReviewTask(processId);
    return new ProcessLink(processId, task == null ? null : task.id(),
        task == null ? null : task.assignee(), task == null ? null : task.definitionKey(), historic);
  }

  @SuppressWarnings("unchecked")
  public TaskInfo secondaryReviewTask(String processInstanceId) {
    String uri = UriComponentsBuilder.fromHttpUrl(baseUrl + "/runtime/tasks")
        .queryParam("processInstanceId", processInstanceId)
        .queryParam("taskDefinitionKey", SECONDARY_REVIEW_TASK_KEY)
        .build().encode().toUriString();
    Map<String, Object> response = rest.getForObject(uri, Map.class);
    List<Map<String, Object>> rows = (List<Map<String, Object>>)
        Objects.requireNonNull(response).get("data");
    if (rows == null || rows.isEmpty()) return null;
    for (Map<String, Object> row : rows) {
      String definitionKey = Objects.toString(row.get("taskDefinitionKey"), null);
      if (SECONDARY_REVIEW_TASK_KEY.equals(definitionKey)) {
        return new TaskInfo(Objects.toString(row.get("id")),
            Objects.toString(row.get("assignee"), null), definitionKey);
      }
    }
    return null;
  }

  public void claim(String taskId, String owner) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    rest.exchange(baseUrl + "/runtime/tasks/" + taskId, HttpMethod.POST,
        new HttpEntity<>(Map.of("action", "claim", "assignee", owner), headers), Void.class);
  }

  public record ProcessLink(
      String processInstanceId, String taskId, String assignee, String taskDefinitionKey,
      boolean ended) {}

  public record TaskInfo(String id, String assignee, String definitionKey) {}
}
