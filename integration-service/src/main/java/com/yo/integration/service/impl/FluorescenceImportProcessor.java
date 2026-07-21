package com.yo.integration.service.impl;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yo.api.client.sample.SampleClient;
import com.yo.api.client.signal.SignalClient;
import com.yo.integration.domain.po.ImportBatch;
import com.yo.integration.domain.po.ImportRowTask;
import com.yo.integration.mapper.ImportBatchMapper;
import com.yo.integration.mapper.ImportRowTaskMapper;
import com.yo.integration.client.SampleWorkflowClient;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.*;
import org.springframework.stereotype.Component;

@Component
public class FluorescenceImportProcessor {
  private static final List<String> CHANNELS =
      List.of("ATTO", "FAM", "HEX", "ROX", "CY5", "CY55", "IRC");
  private static final Pattern TARGET_MAPPING = Pattern.compile(
      "([A-Za-z0-9.]+)\\s*:\\s*\\[\\s*(?:([01AB])_)?([A-Za-z0-9.]+)\\s*]",
      Pattern.CASE_INSENSITIVE);
  private static final Pattern RESULT_ITEM = Pattern.compile(
      "\\[\\s*([^,\\[\\]]+)\\s*,\\s*(阳性|阴性|可疑|无效)\\s*]",
      Pattern.CASE_INSENSITIVE);
  private final MinioClient minio;
  private final SampleClient samples;
  private final SignalClient signals;
  private final ImportBatchMapper batches;
  private final ImportRowTaskMapper tasks;
  private final ObjectMapper json;
  private final SampleWorkflowClient sampleWorkflow;

  public FluorescenceImportProcessor(
      MinioClient minio,
      SampleClient samples,
      SignalClient signals,
      ImportBatchMapper batches,
      ImportRowTaskMapper tasks,
      SampleWorkflowClient sampleWorkflow,
      ObjectMapper json) {
    this.minio = minio;
    this.samples = samples;
    this.signals = signals;
    this.batches = batches;
    this.tasks = tasks;
    this.sampleWorkflow = sampleWorkflow;
    this.json = json;
  }

  public void process(ImportBatch batch, String bucket, String objectKey) {
    batch.status = "PARSING";
    batch.updatedAt = LocalDateTime.now();
    batches.updateById(batch);
    RowListener listener = new RowListener(batch);
    try (InputStream input =
        minio.getObject(GetObjectArgs.builder().bucket(bucket).object(objectKey).build())) {
      EasyExcel.read(input, listener).headRowNumber(1).sheet().doRead();
      batch.totalRows = listener.total;
      batch.successRows = 0;
      batch.errorRows = 0;
      batch.status = listener.total == 0 ? "FAILED" : "PROCESSING";
      batch.failureReason = listener.total == 0 ? "Excel contains no data rows" : null;
    } catch (Exception failure) {
      batch.status = "FAILED";
      batch.failureReason = safe(failure.getMessage());
    }
    batch.updatedAt = LocalDateTime.now();
    batches.updateById(batch);
  }

  public void execute(ImportRowTask task) throws Exception {
    Map<String, String> row = json.readValue(task.rowJson, new TypeReference<>() {});
    importRow(row, task.idempotencyKey);
  }

  final class RowListener extends AnalysisEventListener<Map<Integer, String>> {
    private final ImportBatch batch;
    private final Map<Integer, String> headers = new LinkedHashMap<>();
    int total;

    RowListener(ImportBatch batch) {
      this.batch = batch;
    }

    @Override
    public void invokeHeadMap(Map<Integer, String> headMap, AnalysisContext context) {
      headMap.forEach((index, value) -> headers.put(index, normalizeHeader(value)));
    }

    @Override
    public void invoke(Map<Integer, String> values, AnalysisContext context) {
      total++;
      int rowNo = context.readRowHolder().getRowIndex() + 1;
      Map<String, String> named = new LinkedHashMap<>();
      values.forEach((index, value) -> named.put(headers.getOrDefault(index, ""), value));
      ImportRowTask task = new ImportRowTask();
      task.batchId = batch.id;
      task.rowNo = rowNo;
      task.idempotencyKey = "import:" + batch.batchNo + ":row:" + rowNo;
      try {
        task.rowJson = json.writeValueAsString(named);
      } catch (Exception failure) {
        throw new IllegalStateException("Cannot serialize import row " + rowNo, failure);
      }
      task.status = "READY";
      task.attempts = 0;
      task.nextAttemptAt = LocalDateTime.now();
      task.createdAt = task.updatedAt = LocalDateTime.now();
      tasks.insert(task);
    }

    @Override
    public void doAfterAllAnalysed(AnalysisContext context) {}
  }

  private void importRow(Map<String, String> row, String idempotencyKey) throws Exception {
    String instrument = required(row, "仪器SN", "仪器编号");
    String cartridge = required(row, "卡盒编号", "卡盒号");
    LocalDateTime started = parseTime(required(row, "检测开始时间", "开始时间"));
    String module = first(row, "模块位置", "模块");
    String runNo =
        instrument
            + "-"
            + started.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
            + "-"
            + (module == null ? "0" : module);
    Map<String, String> targetByPath = parseTargetMapping(
        row, required(row, "病原和通道对应关系", "靶标和通道对应关系"));
    Map<String, String> systemResults = parseSystemResults(required(row, "检测结果"));
    String concentrationUnit = Optional.ofNullable(first(row, "浓度单位")).orElse("Copies/mL");
    List<SampleClient.TargetResult> targetResults = new ArrayList<>();
    targetByPath.forEach((path, targetCode) -> {
      String[] parts = path.split("\\|", 2);
      String chamber = parts[0];
      String channel = parts[1];
      try {
        String ctText =
            first(row,
                chamber + "腔室" + excelChannel(channel) + "通道CT值",
                chamber + "腔" + excelChannel(channel) + "通道CT值",
                chamber + "腔室" + channel + "通道CT值");
        String concentrationText =
            first(row,
                chamber + "腔室" + excelChannel(channel) + "通道浓度",
                chamber + "腔" + excelChannel(channel) + "通道浓度",
                chamber + "腔室" + channel + "通道浓度");
        Double ct = parseMeasurement(ctText);
        Double concentration = parseMeasurement(concentrationText);
        String systemLabel = systemResults.getOrDefault(
            targetCode, ct == null ? "NEGATIVE" : "POSITIVE");
        List<String> riskFlags = riskFlags(systemLabel, ct, concentration);
        targetResults.add(
            new SampleClient.TargetResult(
                chamber, channel, targetCode, systemLabel, ct, concentration, concentrationUnit,
                riskFlags.isEmpty() ? "NORMAL" : "WATCH", riskFlags));
      } catch (RuntimeException failure) {
        throw new IllegalArgumentException("Invalid target evidence for " + targetCode, failure);
      }
    });
    Double ircA = readMeasurement(row, "A", "IRC", "CT值");
    Double ircB = readMeasurement(row, "B", "IRC", "CT值");
    Set<String> activeChambers = targetByPath.keySet().stream()
        .map(path -> path.substring(0, 1)).collect(java.util.stream.Collectors.toSet());
    boolean qcPassed = activeChambers.stream().allMatch(chamber ->
        "A".equals(chamber) ? ircA != null : ircB != null);
    String qcStatus = qcPassed ? "PASS" : "INVALID";
    String mappingJson = json.writeValueAsString(targetByPath);
    String resultJson = json.writeValueAsString(systemResults);
    String qcEvidenceJson = json.writeValueAsString(Map.of(
        "A_IRC_CT", ircA == null ? -100D : ircA,
        "B_IRC_CT", ircB == null ? -100D : ircB,
        "logic", "A、B 腔室 IRC 均检出有效 Ct 时质控通过"));
    var aggregate =
        samples
            .importAggregate(
                new SampleClient.ImportAggregateRequest(
                    idempotencyKey,
                    "IMPORT",
                    cartridge,
                    instrument,
                    runNo,
                    cartridge,
                    first(row, "试剂批次", "试剂批号"),
                    isRespiratoryPanel(targetResults) ? "RESPIRATORY_12" : "MULTIPLEX_PCR",
                    module,
                    first(row, "仪器类型"),
                    qcStatus,
                    qcEvidenceJson,
                    mappingJson,
                    resultJson,
                    started,
                    parseOptionalTime(first(row, "检测结束时间", "结束时间")),
                    targetResults))
            .data();
    if (aggregate == null) throw new IllegalStateException("sample-service returned no data");

    Map<String, SampleClient.TargetResult> evidenceByPath = targetResults.stream()
        .collect(java.util.stream.Collectors.toMap(
            target -> target.chamber() + "|" + normalizeChannel(target.channelCode()),
            target -> target));
    int curveCount = 0;
    for (String chamber : List.of("A", "B")) {
      for (String channel : CHANNELS) {
        String rawText = first(row,
            chamber + "腔室原始数据_" + channel,
            chamber + "腔原始数据_" + channel,
            chamber + "腔室原始数据_" + excelChannel(channel));
        if (rawText == null || rawText.isBlank()) continue;
        String correctedText =
            first(row,
                chamber + "腔室矫正后的数据_" + channel,
                chamber + "腔矫正后的数据_" + channel,
                chamber + "腔室矫正后的数据_" + excelChannel(channel));
        List<Double> rawValues = parseCurveOrNull(rawText);
        if (rawValues == null) continue;
        List<Double> correctedValues = parseCurveOrNull(correctedText);
        String targetCode = targetByPath.getOrDefault(
            chamber + "|" + normalizeChannel(channel), "IRC_" + chamber);
        SampleClient.TargetResult evidence = evidenceByPath.get(
            chamber + "|" + normalizeChannel(channel));
        signals.store(
            new SignalClient.StoreCurveRequest(
                runNo,
                chamber,
                channel,
                targetCode,
                "import-v1",
                rawValues,
                correctedValues,
                evidence == null ? null : evidence.ctValue(),
                evidence == null ? null : evidence.concentrationValue(),
                evidence == null ? null : evidence.concentrationUnit(),
                evidence == null ? "NORMAL" : evidence.riskLevel(),
                evidence == null ? List.of() : evidence.riskFlags()));
        curveCount++;
      }
    }
    if (curveCount == 0) throw new IllegalArgumentException("No raw fluorescence curve found");
    sampleWorkflow.importCompleted(aggregate.sampleId());
  }

  private Map<String, String> parseTargetMapping(Map<String, String> row, String value) {
    Map<String, String> result = new LinkedHashMap<>();
    Matcher matcher = TARGET_MAPPING.matcher(value);
    while (matcher.find()) {
      String chamber = matcher.group(2) == null
          ? activeChamber(row)
          : switch (matcher.group(2).toUpperCase(Locale.ROOT)) {
            case "0", "A" -> "A";
            case "1", "B" -> "B";
            default -> throw new IllegalArgumentException("Unsupported chamber mapping");
          };
      result.put(chamber + "|" + normalizeChannel(matcher.group(3)), matcher.group(1).trim());
    }
    if (result.isEmpty()) throw new IllegalArgumentException("Invalid pathogen/channel mapping");
    return result;
  }

  private String activeChamber(Map<String, String> row) {
    int a = availableChannelCount(row, "A");
    int b = availableChannelCount(row, "B");
    if (a > 0 && b == 0) return "A";
    if (b > 0 && a == 0) return "B";
    throw new IllegalArgumentException("Cannot infer chamber for unqualified target mapping");
  }

  private int availableChannelCount(Map<String, String> row, String chamber) {
    int count = 0;
    for (String channel : CHANNELS) {
      String value = first(row,
          chamber + "腔室原始数据_" + channel,
          chamber + "腔室原始数据_" + excelChannel(channel),
          chamber + "腔室" + excelChannel(channel) + "通道CT值");
      if (value != null && !value.isBlank()) count++;
    }
    return count;
  }

  private static boolean isRespiratoryPanel(List<SampleClient.TargetResult> targets) {
    Set<String> respiratory = Set.of(
        "RSV", "ADV", "FluB", "HPIV", "S.P", "C.P",
        "MP", "HRV", "FluA", "nCoV", "H.I", "CoV");
    return !targets.isEmpty() && targets.stream().allMatch(item -> respiratory.contains(item.targetCode()));
  }

  private Map<String, String> parseSystemResults(String value) {
    Map<String, String> result = new LinkedHashMap<>();
    Matcher matcher = RESULT_ITEM.matcher(value);
    while (matcher.find()) result.put(matcher.group(1).trim(), normalizeResult(matcher.group(2)));
    if (result.isEmpty()) throw new IllegalArgumentException("Invalid detection result");
    return result;
  }

  private static String normalizeResult(String value) {
    return switch (value.trim().toUpperCase(Locale.ROOT)) {
      case "阳性", "POSITIVE" -> "POSITIVE";
      case "阴性", "NEGATIVE" -> "NEGATIVE";
      case "可疑", "SUSPICIOUS", "INDETERMINATE" -> "SUSPICIOUS";
      case "无效", "INVALID" -> "INVALID";
      default -> throw new IllegalArgumentException("Unsupported result: " + value);
    };
  }

  private Double readMeasurement(Map<String, String> row, String chamber, String channel, String suffix) {
    return parseMeasurement(first(row,
        chamber + "腔室" + excelChannel(channel) + "通道" + suffix,
        chamber + "腔" + excelChannel(channel) + "通道" + suffix));
  }

  private static Double parseMeasurement(String value) {
    if (value == null || value.isBlank()) return null;
    double parsed = Double.parseDouble(value.trim());
    return parsed <= -99D ? null : parsed;
  }

  private static List<String> riskFlags(String label, Double ct, Double concentration) {
    List<String> flags = new ArrayList<>();
    if ("POSITIVE".equals(label) && ct != null && ct >= 35D) flags.add("BORDERLINE_CT");
    if ("POSITIVE".equals(label) && concentration != null && concentration <= 1_000D)
      flags.add("LOW_CONCENTRATION");
    return List.copyOf(flags);
  }

  private static String normalizeChannel(String value) {
    return value.replace(".", "").toUpperCase(Locale.ROOT);
  }

  private static String excelChannel(String value) {
    return "CY55".equals(normalizeChannel(value)) ? "CY5.5" : value;
  }

  private List<Double> parseCurveOrNull(String value) throws Exception {
    if (value == null || value.isBlank() || "null".equalsIgnoreCase(value.trim())) return null;
    String normalized = value.trim().replace("，", ",");
    if (!normalized.startsWith("[")) normalized = "[" + normalized + "]";
    List<Double> result = json.readValue(normalized, new TypeReference<>() {});
    if (result == null
        || result.size() < 5
        || result.stream().anyMatch(x -> x == null || !Double.isFinite(x))) return null;
    return List.copyOf(result);
  }

  private String required(Map<String, String> row, String... aliases) {
    String value = first(row, aliases);
    if (value == null || value.isBlank())
      throw new IllegalArgumentException("Missing required field: " + aliases[0]);
    return value.trim();
  }

  private String first(Map<String, String> row, String... aliases) {
    for (String alias : aliases) {
      String value = row.get(normalizeHeader(alias));
      if (value != null) return value;
    }
    return null;
  }

  static String normalizeHeader(String value) {
    return value == null
        ? ""
        : value
            .replaceAll("\\s+", "")
            .replace("ＣＴ", "CT")
            .replace("Ct", "CT")
            .toUpperCase(Locale.ROOT);
  }

  static LocalDateTime parseTime(String value) {
    String normalized = value.trim().replace('/', '-');
    for (DateTimeFormatter formatter :
        List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-M-d H:m:s"),
            DateTimeFormatter.ISO_LOCAL_DATE_TIME)) {
      try {
        return LocalDateTime.parse(normalized, formatter);
      } catch (RuntimeException ignored) {
      }
    }
    throw new IllegalArgumentException("Invalid detection time");
  }

  private static LocalDateTime parseOptionalTime(String value) {
    return value == null || value.isBlank() ? null : parseTime(value);
  }

  static String safe(String message) {
    if (message == null) return "Unknown import error";
    return message.length() <= 500 ? message : message.substring(0, 500);
  }
}
