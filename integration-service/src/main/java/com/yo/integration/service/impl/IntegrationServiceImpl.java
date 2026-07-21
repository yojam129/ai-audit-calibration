package com.yo.integration.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yo.common.domain.vo.PageVO;
import com.yo.integration.domain.dto.*;
import com.yo.integration.domain.po.*;
import com.yo.integration.domain.query.ImportQuery;
import com.yo.integration.domain.vo.*;
import com.yo.integration.mapper.*;
import com.yo.integration.service.IntegrationService;
import io.minio.*;
import io.minio.http.Method;
import java.io.InputStream;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IntegrationServiceImpl implements IntegrationService {
  private final FileAssetMapper assets;
  private final ImportBatchMapper batches;
  private final ImportErrorMapper importErrors;
  private final MinioClient minio;
  private final FluorescenceImportProcessor importProcessor;
  private final ImportRowTaskWorker taskWorker;
  private final com.yo.integration.service.ImportRecoveryService recovery;
  private final String bucket;

  public IntegrationServiceImpl(
      FileAssetMapper a,
      ImportBatchMapper b,
      ImportErrorMapper importErrors,
      MinioClient m,
      FluorescenceImportProcessor importProcessor,
      ImportRowTaskWorker taskWorker,
      com.yo.integration.service.ImportRecoveryService recovery,
      @Value("${app.minio.incoming-bucket}") String bucket) {
    this.assets = a;
    this.batches = b;
    this.importErrors = importErrors;
    this.minio = m;
    this.importProcessor = importProcessor;
    this.taskWorker = taskWorker;
    this.recovery = recovery;
    this.bucket = bucket;
  }

  @Transactional
  public PresignVO presign(PresignDTO d) {
    try {
      String no = UUID.randomUUID().toString();
      String key = LocalDate.now() + "/" + no + "/" + d.fileName().replaceAll("[\\\\/]", "_");
      FileAsset a = new FileAsset();
      a.assetNo = no;
      a.bucketName = bucket;
      a.objectKey = key;
      a.originalName = d.fileName();
      a.contentType = d.contentType();
      a.sizeBytes = d.sizeBytes();
      a.sha256 = d.sha256();
      a.status = "PENDING_UPLOAD";
      a.createdAt = LocalDateTime.now();
      assets.insert(a);
      String url =
          minio.getPresignedObjectUrl(
              GetPresignedObjectUrlArgs.builder()
                  .method(Method.PUT)
                  .bucket(bucket)
                  .object(key)
                  .expiry(10, TimeUnit.MINUTES)
                  .build());
      return new PresignVO(a.id, no, bucket, key, url, Instant.now().plusSeconds(600));
    } catch (Exception e) {
      throw new IllegalStateException("Unable to create upload URL", e);
    }
  }

  @Transactional
  public ImportVO createImport(CreateImportDTO d) {
    FileAsset asset = assets.selectById(d.assetId());
    if (asset == null) throw new IllegalArgumentException("File asset not found");
    if (!"READY".equals(asset.status)) throw new IllegalStateException("Upload not confirmed");
    ImportBatch existing =
        batches.selectOne(
            new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<ImportBatch>()
                .eq("asset_id", d.assetId())
                .eq("business_type", d.businessType())
                .eq("template_version", d.templateVersion())
                .last("LIMIT 1"));
    if (existing != null) return vo(existing);
    ImportBatch b = new ImportBatch();
    b.batchNo = UUID.randomUUID().toString();
    b.assetId = d.assetId();
    b.businessType = d.businessType();
    b.templateVersion = d.templateVersion();
    b.status = "CREATED";
    b.totalRows = b.successRows = b.errorRows = 0;
    b.version = 0;
    b.createdAt = b.updatedAt = LocalDateTime.now();
    batches.insert(b);
    if ("FLUORESCENCE".equalsIgnoreCase(d.businessType())
        || "FLUORESCENCE_RAW".equalsIgnoreCase(d.businessType())
        || "POSITIVE_RATE_HISTORY".equalsIgnoreCase(d.businessType())) {
      importProcessor.process(b, asset.bucketName, asset.objectKey);
      if ("FAILED".equals(b.status)) recovery.ensureOpen(b);
    } else {
      b.status = "FAILED";
      b.failureReason = "Unsupported business type: " + d.businessType();
      b.updatedAt = LocalDateTime.now();
      batches.updateById(b);
      recovery.ensureOpen(b);
    }
    return vo(b);
  }

  @Transactional
  public FileAssetVO confirmUpload(ConfirmUploadDTO dto) {
    FileAsset asset = requireAsset(dto.assetId());
    if ("READY".equals(asset.status)) return assetVo(asset);
    try (InputStream input =
        minio.getObject(
            GetObjectArgs.builder().bucket(asset.bucketName).object(asset.objectKey).build())) {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      long size = 0;
      byte[] buffer = new byte[8192];
      for (int read; (read = input.read(buffer)) >= 0; ) {
        digest.update(buffer, 0, read);
        size += read;
      }
      String hash = HexFormat.of().formatHex(digest.digest());
      if (size != dto.sizeBytes()
          || size != asset.sizeBytes
          || !hash.equalsIgnoreCase(dto.sha256())
          || !hash.equalsIgnoreCase(asset.sha256)) {
        asset.status = "CHECKSUM_FAILED";
        assets.updateById(asset);
        throw new IllegalArgumentException("Uploaded object checksum or size mismatch");
      }
      asset.status = "READY";
      assets.updateById(asset);
      return assetVo(asset);
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalStateException("Unable to confirm upload", e);
    }
  }

  public FileAssetVO getAsset(long id) {
    return assetVo(requireAsset(id));
  }

  private FileAsset requireAsset(long id) {
    FileAsset asset = assets.selectById(id);
    if (asset == null) throw new IllegalArgumentException("File asset not found");
    return asset;
  }

  private FileAssetVO assetVo(FileAsset asset) {
    return new FileAssetVO(
        asset.id,
        asset.assetNo,
        asset.bucketName,
        asset.objectKey,
        asset.originalName,
        asset.sizeBytes,
        asset.sha256,
        asset.status,
        asset.createdAt);
  }

  public PageVO<ImportVO> page(ImportQuery q) {
    var w =
        new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<ImportBatch>()
            .eq(q.status() != null && !q.status().isBlank(), "status", q.status())
            .eq(
                q.businessType() != null && !q.businessType().isBlank(),
                "business_type",
                q.businessType())
            .orderByDesc("created_at");
    var p = batches.selectPage(new Page<>(q.pageNo(), q.pageSize()), w);
    return new PageVO<>(
        p.getTotal(),
        p.getPages(),
        p.getCurrent(),
        p.getSize(),
        p.getRecords().stream().map(this::vo).toList());
  }

  public List<ImportErrorVO> errors(long batchId) {
    return importErrors
        .selectList(
            new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<
                    com.yo.integration.domain.po.ImportError>()
                .eq("batch_id", batchId)
                .orderByAsc("row_no", "id"))
        .stream()
        .map(
            error ->
                new ImportErrorVO(
                    error.rowNo, error.columnName, error.errorCode, error.errorMessage))
        .toList();
  }

  public boolean retryFailedRow(long batchId, int rowNo) {
    return taskWorker.retry(batchId, rowNo);
  }

  public boolean resolveFailedRow(long batchId, int rowNo, String resolution, String reason) {
    return recovery.resolve(batchId, rowNo, resolution, reason);
  }

  public ImportVO resolveFailedBatch(long batchId, String resolution, String reason) {
    if (!recovery.resolveBatch(batchId, resolution, reason))
      throw new IllegalStateException("Import batch is not recoverable");
    if ("RETRY".equalsIgnoreCase(resolution)) recovery.resumeBatchRetry(batchId);
    return vo(batches.selectById(batchId));
  }

  private ImportVO vo(ImportBatch b) {
    return new ImportVO(
        b.id,
        b.batchNo,
        b.businessType,
        b.status,
        b.totalRows,
        b.successRows,
        b.errorRows,
        b.failureReason,
        b.processInstanceId,
        b.flowableTaskId,
        b.createdAt);
  }
}
