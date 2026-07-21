package com.yo.integration.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yo.api.client.alert.PositiveRateImportClient;
import com.yo.integration.domain.po.ImportRowTask;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.util.*;
import java.util.regex.*;
import org.springframework.stereotype.Component;

@Component
public class PositiveRateHistoryImportProcessor {
  private static final Pattern RESULT_ITEM = Pattern.compile(
      "\\[\\s*([^,\\[\\]]+)\\s*,\\s*(阳性|阴性|可疑|无效)\\s*]",
      Pattern.CASE_INSENSITIVE);
  private static final Map<String, String> RESPIRATORY_PATHS = Map.ofEntries(
      Map.entry("RSV", "A|ATTO"), Map.entry("ADV", "A|FAM"),
      Map.entry("FluB", "A|HEX"), Map.entry("HPIV", "A|ROX"),
      Map.entry("S.P", "A|CY5"), Map.entry("C.P", "A|CY5.5"),
      Map.entry("MP", "B|ATTO"), Map.entry("HRV", "B|FAM"),
      Map.entry("FluA", "B|HEX"), Map.entry("nCoV", "B|ROX"),
      Map.entry("H.I", "B|CY5"), Map.entry("CoV", "B|CY5.5"));

  private final PositiveRateImportClient alerts;
  private final ObjectMapper json;

  public PositiveRateHistoryImportProcessor(PositiveRateImportClient alerts, ObjectMapper json) {
    this.alerts = alerts;
    this.json = json;
  }

  public void execute(ImportRowTask task) throws Exception {
    Map<String, String> row = json.readValue(task.rowJson, new TypeReference<>() {});
    String orderId = required(row, "检测单ID");
    String panelCode = required(row, "卡盒");
    String instrumentNo = required(row, "仪器SN");
    String reagentLotNo = required(row, "试剂批次");
    String concentrationUnit = Optional.ofNullable(first(row, "浓度单位")).orElse("Copies/mL");
    var occurredAt = FluorescenceImportProcessor.parseTime(required(row, "检测开始时间"))
        .atZone(ZoneId.of("Asia/Shanghai")).toInstant();
    List<PositiveRateImportClient.TargetResult> targets = new ArrayList<>();
    Matcher matcher = RESULT_ITEM.matcher(required(row, "检测结果"));
    while (matcher.find()) {
      String targetCode = matcher.group(1).trim();
      String label = normalizeResult(matcher.group(2));
      String path = "161".equals(panelCode) ? RESPIRATORY_PATHS.get(targetCode) : null;
      Double ct = path == null ? null : measurement(row, path, "CT值");
      Double concentration = path == null ? null : measurement(row, path, "浓度");
      String riskLevel = "POSITIVE".equals(label)
              && ((ct != null && ct >= 35D) || (concentration != null && concentration <= 1_000D))
          ? "WATCH" : "NORMAL";
      targets.add(new PositiveRateImportClient.TargetResult(
          targetCode, label, ct, concentration, concentrationUnit, riskLevel));
    }
    if (targets.isEmpty()) throw new IllegalArgumentException("Invalid detection result");
    UUID eventId = UUID.nameUUIDFromBytes(
        (task.idempotencyKey + ":POSITIVE_RATE_HISTORY").getBytes(StandardCharsets.UTF_8));
    var response = alerts.importFacts(new PositiveRateImportClient.DetectionFactRequest(
        eventId, "IMPORT", orderId, instrumentNo, panelCode, reagentLotNo, occurredAt, targets));
    if (response == null || !Boolean.TRUE.equals(response.data()))
      throw new IllegalStateException("alert-service rejected historical detection facts");
  }

  private Double measurement(Map<String, String> row, String path, String suffix) {
    String[] parts = path.split("\\|", 2);
    String value = first(row, parts[0] + "腔室" + parts[1] + "通道" + suffix);
    if (value == null || value.isBlank()) return null;
    double parsed = Double.parseDouble(value);
    return parsed <= -99D ? null : parsed;
  }

  private static String normalizeResult(String value) {
    return switch (value.trim()) {
      case "阳性" -> "POSITIVE";
      case "阴性" -> "NEGATIVE";
      case "可疑" -> "SUSPICIOUS";
      case "无效" -> "INVALID";
      default -> throw new IllegalArgumentException("Unsupported result: " + value);
    };
  }

  private String required(Map<String, String> row, String name) {
    String value = first(row, name);
    if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing field: " + name);
    return value.trim();
  }

  private String first(Map<String, String> row, String name) {
    return row.get(FluorescenceImportProcessor.normalizeHeader(name));
  }
}
